package server

import (
	"context"
	"errors"
	"io"
	"log"
	"net"
	"strconv"
	"time"

	"github.com/go-redis/redis/v8"

	"golang-gateway-tcp-server/internal/protocol"
	"golang-gateway-tcp-server/internal/registry"
	"golang-gateway-tcp-server/internal/service"
	"golang-gateway-tcp-server/internal/session"
)

type TCPServer struct {
	addr        string
	sessions    *session.Manager
	keepAlive   *service.DeviceKeepAliveService
	online      *registry.DeviceOnlineRegistry
	executor    *service.UpgradeExecutor
	redis       *redis.Client
	idleTimeout time.Duration
}

func NewTCPServer(addr string, sessions *session.Manager, keepAlive *service.DeviceKeepAliveService, online *registry.DeviceOnlineRegistry, executor *service.UpgradeExecutor, redisClient *redis.Client) *TCPServer {
	return &TCPServer{
		addr:        addr,
		sessions:    sessions,
		keepAlive:   keepAlive,
		online:      online,
		executor:    executor,
		redis:       redisClient,
		idleTimeout: 180 * time.Second,
	}
}

func (s *TCPServer) ListenAndServe(ctx context.Context) error {
	ln, err := net.Listen("tcp", s.addr)
	if err != nil {
		return err
	}
	log.Printf("TCP server started on %s", s.addr)
	go func() {
		<-ctx.Done()
		_ = ln.Close()
	}()

	for {
		conn, err := ln.Accept()
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			log.Printf("accept tcp connection failed: %v", err)
			continue
		}
		go s.handleConn(ctx, conn)
	}
}

func (s *TCPServer) handleConn(parent context.Context, conn net.Conn) {
	defer conn.Close()
	ctx, cancel := context.WithCancel(parent)
	defer cancel()

	for {
		_ = conn.SetReadDeadline(time.Now().Add(s.idleTimeout))
		frame, err := protocol.ReadFrame(conn)
		if err != nil {
			if ne, ok := err.(net.Error); ok && ne.Timeout() {
				log.Printf("[UpgradeDispatchHandler] reader idle, close channel")
			} else if !errors.Is(err, io.EOF) {
				log.Printf("read fota frame failed: %v", err)
			}
			s.onInactive(ctx, conn)
			return
		}
		msg, err := protocol.DecodeMessage(frame)
		if err != nil {
			log.Printf("decode fota message failed: %v", err)
			s.onInactive(ctx, conn)
			return
		}
		if !s.identify(ctx, conn, msg) {
			s.onInactive(ctx, conn)
			return
		}
		s.dispatch(ctx, msg)
	}
}

func (s *TCPServer) identify(ctx context.Context, conn net.Conn, msg protocol.Message) bool {
	imei := msg.IMEI()
	if imei == "" {
		_ = conn.Close()
		return false
	}
	current := s.sessions.GetByConn(conn)
	deviceID, err := s.keepAlive.GetDeviceID(ctx, imei)
	if err != nil {
		log.Printf("get device id failed: %v", err)
		_ = conn.Close()
		return false
	}

	var deviceSession *session.DeviceSession
	if current == nil || current.IMEI != imei {
		deviceSession = s.sessions.Bind(imei, deviceID, conn)
		s.keepAlive.OnDeviceFirstConnect(ctx, deviceID)
		firmwareVersion := ""
		deviceType := ""
		if boot, ok := msg.(protocol.DeviceBootUpMessage); ok {
			firmwareVersion = boot.FirmwareVersion
			deviceType = boot.DeviceType
		}
		remote := ""
		if conn.RemoteAddr() != nil {
			remote = conn.RemoteAddr().String()
		}
		if err := s.online.Register(ctx, deviceSession, firmwareVersion, deviceType, remote); err != nil {
			log.Printf("register online route failed: %v", err)
		}
	} else {
		deviceSession = s.sessions.Touch(conn)
		s.online.Touch(ctx, deviceSession)
	}

	if _, ok := msg.(protocol.HeartbeatMessage); ok {
		s.keepAlive.RefreshHeartbeat(ctx, deviceID)
		s.online.Touch(ctx, deviceSession)
	}
	if task := msg.TaskID(); task != nil {
		s.sessions.SetCurrentTask(conn, *task)
	}
	return true
}

func (s *TCPServer) dispatch(ctx context.Context, msg protocol.Message) {
	switch m := msg.(type) {
	case protocol.DeviceBootUpMessage:
		s.executor.OnDeviceBootUp(ctx, m)
	case protocol.HeartbeatMessage:
		s.executor.BreakpointResume(ctx, m)
	case protocol.ACKMessage:
		switch m.ACKType {
		case protocol.ACKTypeUpgradeRequest, protocol.ACKTypePacket, protocol.MockDeviceBusy:
			s.executor.ReceiveUpgradeRequestAckAndSendSplitPacket(ctx, m)
		case protocol.ACKTypeCancel:
			s.executor.ReceiveCancelAck(ctx, m)
		}
	case protocol.FailMessage:
		log.Printf("[UpgradeDispatchHandler] device FAIL ignored, imei=%s taskId=%d packetNo=%d errorCode=%d", m.IMEI(), m.Task, m.Packet, m.ErrorCode)
	case protocol.UpgradeResultMessage:
		s.executor.ReceiveUpgradeResult(ctx, m)
	default:
		log.Printf("[UpgradeDispatchHandler] ignored inbound message: %T", msg)
	}
}

func (s *TCPServer) onInactive(ctx context.Context, conn net.Conn) {
	deviceSession := s.sessions.Remove(conn)
	if deviceSession == nil {
		return
	}
	s.online.RemoveIfSessionMatches(ctx, deviceSession.IMEI, deviceSession.SessionID)
	runtimeKey := service.UpgradeRuntimeKeyPrefix + deviceSession.IMEI
	status, _ := s.redis.HGet(ctx, runtimeKey, "status").Result()
	if (status == "UPGRADING" || status == "UPGRADE_REQUESTED") && deviceSession.CurrentTaskID != nil {
		s.executor.PushDisconnect(ctx, deviceSession.IMEI, *deviceSession.CurrentTaskID)
		_ = s.redis.HSet(ctx, runtimeKey, "lastPacketAt", strconv.FormatInt(time.Now().UnixMilli(), 10)).Err()
	}
}
