package session

import (
	"log"
	"net"
	"sync"
	"time"

	"github.com/google/uuid"
)

type ConnWriter interface {
	Write([]byte) (int, error)
	Close() error
	RemoteAddr() net.Addr
}

type DeviceSession struct {
	IMEI           string
	SessionID      string
	DeviceID       int64
	Conn           ConnWriter
	ConnectTime    int64
	LastActiveTime int64
	CurrentTaskID  *int64
}

type DeviceKeepAlive interface {
	OnDeviceOffline(deviceID int64)
}

type Manager struct {
	mu              sync.RWMutex
	byIMEI          map[string]*DeviceSession
	byConn          map[ConnWriter]*DeviceSession
	deviceKeepAlive DeviceKeepAlive
}

func NewManager(deviceKeepAlive DeviceKeepAlive) *Manager {
	return &Manager{
		byIMEI:          make(map[string]*DeviceSession),
		byConn:          make(map[ConnWriter]*DeviceSession),
		deviceKeepAlive: deviceKeepAlive,
	}
}

func (m *Manager) Bind(imei string, deviceID int64, conn ConnWriter) *DeviceSession {
	m.mu.Lock()
	defer m.mu.Unlock()

	if old := m.byIMEI[imei]; old != nil {
		if old.Conn == conn {
			old.LastActiveTime = time.Now().UnixMilli()
			return old
		}
		_ = old.Conn.Close()
		delete(m.byConn, old.Conn)
	}

	now := time.Now().UnixMilli()
	existing := m.byConn[conn]
	sessionID := uuid.NewString()
	if existing != nil && existing.SessionID != "" {
		sessionID = existing.SessionID
	}

	s := &DeviceSession{
		IMEI:           imei,
		SessionID:      sessionID,
		DeviceID:       deviceID,
		Conn:           conn,
		ConnectTime:    now,
		LastActiveTime: now,
	}
	m.byIMEI[imei] = s
	m.byConn[conn] = s
	return s
}

func (m *Manager) GetByIMEI(imei string) *DeviceSession {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.byIMEI[imei]
}

func (m *Manager) GetByConn(conn ConnWriter) *DeviceSession {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.byConn[conn]
}

func (m *Manager) Touch(conn ConnWriter) *DeviceSession {
	m.mu.Lock()
	defer m.mu.Unlock()
	s := m.byConn[conn]
	if s != nil {
		s.LastActiveTime = time.Now().UnixMilli()
	}
	return s
}

func (m *Manager) SetCurrentTask(conn ConnWriter, taskID int64) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if s := m.byConn[conn]; s != nil {
		s.CurrentTaskID = &taskID
	}
}

func (m *Manager) Remove(conn ConnWriter) *DeviceSession {
	m.mu.Lock()
	defer m.mu.Unlock()
	s := m.byConn[conn]
	if s == nil {
		return nil
	}
	delete(m.byConn, conn)
	delete(m.byIMEI, s.IMEI)
	if m.deviceKeepAlive != nil {
		m.deviceKeepAlive.OnDeviceOffline(s.DeviceID)
	}
	log.Printf("[SessionManager] remove session, imei=%s", s.IMEI)
	return s
}

func (m *Manager) OnlineSessionCount() int {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return len(m.byIMEI)
}
