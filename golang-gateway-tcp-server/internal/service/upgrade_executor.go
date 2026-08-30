package service

import (
	"context"
	"encoding/hex"
	"fmt"
	"log"
	"strconv"
	"time"

	"github.com/go-redis/redis/v8"

	"golang-gateway-tcp-server/internal/protocol"
)

const runtimeCheckpointPacketInterval = 10

type UpgradeExecutor struct {
	redis    *redis.Client
	sender   *PacketSender
	events   *EventProducer
	locks    *DeviceUpgradeLockService
	firmware *FirmwareCacheManager
}

func NewUpgradeExecutor(redisClient *redis.Client, sender *PacketSender, events *EventProducer, locks *DeviceUpgradeLockService, firmware *FirmwareCacheManager) *UpgradeExecutor {
	return &UpgradeExecutor{redis: redisClient, sender: sender, events: events, locks: locks, firmware: firmware}
}

func (e *UpgradeExecutor) OnDeviceBootUp(ctx context.Context, msg protocol.DeviceBootUpMessage) {
	e.sender.SendToDevice(msg.IMEI(), protocol.PlatformCommonACK{
		Imei:           msg.IMEI(),
		Task:           0,
		RefMessageType: protocol.MsgDeviceBootUp,
		ACKStatus:      0,
		ReasonCode:     0,
	})
}

func (e *UpgradeExecutor) ReceiveUpgradeResult(ctx context.Context, msg protocol.UpgradeResultMessage) {
	e.sender.SendToDevice(msg.IMEI(), protocol.PlatformCommonACK{
		Imei:           msg.IMEI(),
		Task:           msg.Task,
		RefMessageType: msg.MessageType(),
		ACKStatus:      0,
		ReasonCode:     0,
	})

	runtimeKey := UpgradeRuntimeKeyPrefix + msg.IMEI()
	runtimeMap, _ := e.redis.HGetAll(ctx, runtimeKey).Result()
	finalStatus := "FAIL"
	if msg.Result == 0 {
		finalStatus = "SUCCESS"
	}
	now := time.Now().UnixMilli()
	_ = e.redis.HSet(ctx, runtimeKey, map[string]interface{}{
		"endAt":        strconv.FormatInt(now, 10),
		"progress":     "100",
		"packetTime":   strconv.FormatInt(now, 10),
		"lastPacketAt": strconv.FormatInt(now, 10),
		"status":       finalStatus,
		"costTime":     strconv.Itoa(int(msg.CostTime)),
	}).Err()

	e.events.Send(ctx, EventProgress, "PROGRESS", UpgradeEventMessage{
		IMEI:          msg.IMEI(),
		TaskID:        Int64Ptr(msg.Task),
		UpgradeStatus: finalStatus,
		Progress:      IntPtr(100),
	})
	progress := 100
	currentPacket := 0
	totalPacket := 0
	errorCode := ""
	if finalStatus != "SUCCESS" {
		errorCode = strconv.Itoa(msg.ErrorCode)
	}
	e.events.Send(ctx, EventFinalResult, "FINAL_RESULT", UpgradeEventMessage{
		IMEI:                  msg.IMEI(),
		TaskID:                Int64Ptr(msg.Task),
		UpgradeStatus:         finalStatus,
		Progress:              &progress,
		TargetFirmwareVersion: runtimeMap["targetFirmwareVersion"],
		CurrentPacket:         &currentPacket,
		TotalPacket:           &totalPacket,
		FailReason:            errorCode,
		EndTime:               time.Now().Format(time.RFC3339),
	})

	e.releaseRuntimeLock(ctx, msg.IMEI(), runtimeMap, strconv.FormatInt(msg.Task, 10))
	if firmwareID, err := strconv.ParseInt(runtimeMap["firmwareId"], 10, 64); err == nil {
		e.firmware.Decrement(firmwareID)
	}
}

func (e *UpgradeExecutor) ReceiveUpgradeRequestAckAndSendSplitPacket(ctx context.Context, ack protocol.ACKMessage) {
	runtimeKey := UpgradeRuntimeKeyPrefix + ack.IMEI()
	runtimeMap, err := e.redis.HGetAll(ctx, runtimeKey).Result()
	if err != nil || len(runtimeMap) == 0 {
		return
	}
	taskID, _ := strconv.ParseInt(runtimeMap["taskId"], 10, 64)
	if ack.Task != taskID {
		return
	}
	holder := e.firmware.Get(mustInt64(runtimeMap["firmwareId"]))
	if holder == nil {
		log.Printf("固件不存在，无法进行0x82分包下发过程 imei=%s taskId=%d", ack.IMEI(), ack.Task)
		return
	}

	totalPacket := int32(mustInt64(runtimeMap["totalPacket"]))
	chunkSize := int32(mustInt64(runtimeMap["chunkSize"]))
	fileSize := mustInt64(runtimeMap["fileSize"])
	nextPacketNo, ok := resolveNextPacketNo(ack, int32(mustInt64(runtimeMap["packetNo"])))
	if !ok || nextPacketNo > totalPacket {
		return
	}

	if nextPacketNo == 1 {
		e.events.Send(ctx, EventUpgrading, "UPGRADING", UpgradeEventMessage{
			IMEI:          ack.IMEI(),
			TaskID:        Int64Ptr(ack.Task),
			UpgradeStatus: "UPGRADING",
		})
	}

	chunk, offset, progress, ok := buildChunk(holder.Bytes, nextPacketNo, totalPacket, chunkSize, fileSize)
	if !ok {
		return
	}
	if ack.ACKType == protocol.MockDeviceBusy {
		time.Sleep(2 * time.Second)
	}
	e.sender.SendToDevice(ack.IMEI(), protocol.UpgradePacketMessage{
		Imei:        ack.IMEI(),
		Task:        ack.Task,
		PacketNo:    nextPacketNo,
		TotalPacket: totalPacket,
		ChunkData:   chunk,
	})
	e.updateRuntimeAfterSend(ctx, runtimeKey, nextPacketNo, totalPacket, chunkSize, offset, len(chunk), progress)
	e.pushUpgradeProgressIfNecessary(ctx, runtimeKey, ack, nextPacketNo, totalPacket, progress)
}

func (e *UpgradeExecutor) ReceiveCancelAck(ctx context.Context, ack protocol.ACKMessage) {
	runtimeKey := UpgradeRuntimeKeyPrefix + ack.IMEI()
	runtimeMap, _ := e.redis.HGetAll(ctx, runtimeKey).Result()
	_ = e.redis.HSet(ctx, runtimeKey, "status", "CANCEL_UPGRADE").Err()
	e.releaseRuntimeLock(ctx, ack.IMEI(), runtimeMap, strconv.FormatInt(ack.Task, 10))
	e.events.Send(ctx, EventCancel, "CANCEL_RESULT", UpgradeEventMessage{
		IMEI:          ack.IMEI(),
		TaskID:        Int64Ptr(ack.Task),
		UpgradeStatus: "CANCEL_UPGRADE",
	})
	if firmwareID, err := strconv.ParseInt(runtimeMap["firmwareId"], 10, 64); err == nil {
		e.firmware.Decrement(firmwareID)
	}
}

func (e *UpgradeExecutor) BreakpointResume(ctx context.Context, heartbeat protocol.HeartbeatMessage) {
	runtimeKey := UpgradeRuntimeKeyPrefix + heartbeat.IMEI()
	runtimeMap, err := e.redis.HGetAll(ctx, runtimeKey).Result()
	if err != nil || len(runtimeMap) == 0 || runtimeMap["imei"] != heartbeat.IMEI() || runtimeMap["status"] != "DISCONNECT" {
		return
	}
	lastPacketAt := mustInt64(runtimeMap["lastPacketAt"])
	if time.Since(time.UnixMilli(lastPacketAt)) >= 5*time.Minute {
		return
	}

	taskID := mustInt64(runtimeMap["taskId"])
	packetNo := int32(mustInt64(runtimeMap["packetNo"]))
	if packetNo == 0 {
		packetNo = 1
	}
	totalPacket := int32(mustInt64(runtimeMap["totalPacket"]))
	chunkSize := int32(mustInt64(runtimeMap["chunkSize"]))
	fileSize := mustInt64(runtimeMap["fileSize"])
	if packetNo > totalPacket {
		return
	}

	holder := e.firmware.Get(mustInt64(runtimeMap["firmwareId"]))
	if holder == nil {
		var loadCtx context.Context
		var cancel context.CancelFunc
		loadCtx, cancel = context.WithTimeout(ctx, 10*time.Second)
		defer cancel()
		holder, err = e.firmware.Load(loadCtx, runtimeMap)
		if err != nil || holder == nil {
			log.Printf("breakpointResume 固件缓存加载失败 imei=%s taskId=%d err=%v", heartbeat.IMEI(), taskID, err)
			return
		}
	}

	if packetNo == 1 {
		e.events.Send(ctx, EventUpgrading, "UPGRADING", UpgradeEventMessage{IMEI: heartbeat.IMEI(), TaskID: Int64Ptr(taskID), UpgradeStatus: "UPGRADING"})
		e.events.Send(ctx, EventStartTime, "START_TIME", UpgradeEventMessage{IMEI: heartbeat.IMEI(), TaskID: Int64Ptr(taskID), StartTime: time.Now().Format(time.RFC3339)})
	}

	chunk, offset, progress, ok := buildChunk(holder.Bytes, packetNo, totalPacket, chunkSize, fileSize)
	if !ok {
		return
	}
	e.sender.SendToDevice(heartbeat.IMEI(), protocol.UpgradePacketMessage{
		Imei:        heartbeat.IMEI(),
		Task:        taskID,
		PacketNo:    packetNo,
		TotalPacket: totalPacket,
		ChunkData:   chunk,
	})
	e.writeRuntimeCheckpoint(ctx, runtimeKey, packetNo, totalPacket, chunkSize, offset, len(chunk), progress, time.Now().UnixMilli())
	e.events.Send(ctx, EventProgress, "PROGRESS", UpgradeEventMessage{
		IMEI:          heartbeat.IMEI(),
		TaskID:        Int64Ptr(taskID),
		UpgradeStatus: "RESUME_UPGRADING",
		Progress:      IntPtr(progress),
	})
}

func (e *UpgradeExecutor) SendUpgradeRequest(ctx context.Context, req PlatformUpgradeRequest) error {
	md5Bytes, err := hex.DecodeString(req.MD5)
	if err != nil || len(md5Bytes) != 16 {
		return fmt.Errorf("md5 must be 32 hex chars")
	}
	if !e.locks.Acquire(ctx, req.IMEI, req.LockToken) {
		return fmt.Errorf("设备升级会话已存在，拒绝重复受理")
	}
	runtimeMap := map[string]string{
		"imei":                  req.IMEI,
		"taskId":                strconv.FormatInt(req.TaskID, 10),
		"lockToken":             req.LockToken,
		"status":                "UPGRADE_REQUESTED",
		"startAt":               "0",
		"endAt":                 "0",
		"progress":              "0",
		"firmwareId":            strconv.FormatInt(req.FirmwareID, 10),
		"bucketName":            req.BucketName,
		"objectName":            req.ObjectName,
		"packetNo":              "0",
		"totalPacket":           strconv.Itoa(req.TotalPacket),
		"chunkSize":             strconv.Itoa(req.ChunkSize),
		"fileSize":              strconv.FormatInt(req.FileSize, 10),
		"md5":                   req.MD5,
		"targetFirmwareVersion": req.FirmwareVersionName,
	}
	runtimeKey := UpgradeRuntimeKeyPrefix + req.IMEI
	if err := e.redis.HSet(ctx, runtimeKey, runtimeMap).Err(); err != nil {
		e.locks.Release(ctx, req.IMEI, req.LockToken)
		return err
	}

	loadCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	holder, err := e.firmware.Load(loadCtx, runtimeMap)
	if err != nil || holder == nil || holder.FirmwareID != req.FirmwareID {
		e.locks.Release(ctx, req.IMEI, req.LockToken)
		return fmt.Errorf("固件缓存加载失败，拒绝下发升级请求: %w", err)
	}

	ok := e.sender.SendToDevice(req.IMEI, protocol.UpgradeRequestMessage{
		Imei:                req.IMEI,
		Task:                req.TaskID,
		FirmwareID:          req.FirmwareID,
		FirmwareName:        req.FirmwareName,
		FirmwareVersionName: req.FirmwareVersionName,
		TotalPacket:         int32(req.TotalPacket),
		ChunkSize:           int32(req.ChunkSize),
		FileSize:            req.FileSize,
		MD5:                 md5Bytes,
	})
	if !ok {
		e.locks.Release(ctx, req.IMEI, req.LockToken)
		return fmt.Errorf("设备不在线，无法下发升级请求")
	}
	e.locks.MarkActive(ctx, req.IMEI)
	return nil
}

func (e *UpgradeExecutor) SendCancelUpgradeRequest(ctx context.Context, req PlatformCancelUpgradeRequest) error {
	ok := e.sender.SendToDevice(req.IMEI, protocol.CancelUpgradeMessage{
		Imei:   req.IMEI,
		Task:   req.TaskID,
		Reason: req.Reason,
	})
	if !ok {
		return fmt.Errorf("设备不在线，无法下发取消升级指令")
	}
	return nil
}

func (e *UpgradeExecutor) PushDisconnect(ctx context.Context, imei string, taskID int64) {
	e.events.Send(ctx, EventDisconnect, "DISCONNECT", UpgradeEventMessage{
		IMEI:          imei,
		TaskID:        Int64Ptr(taskID),
		UpgradeStatus: "DISCONNECT",
	})
	_ = e.redis.HSet(ctx, UpgradeRuntimeKeyPrefix+imei, "status", "DISCONNECT").Err()
}

func (e *UpgradeExecutor) updateRuntimeAfterSend(ctx context.Context, runtimeKey string, packetNo, totalPacket, chunkSize int32, offset, chunkLen, progress int) {
	if packetNo != 1 && packetNo < totalPacket && packetNo%runtimeCheckpointPacketInterval != 0 {
		return
	}
	e.writeRuntimeCheckpoint(ctx, runtimeKey, packetNo, totalPacket, chunkSize, offset, chunkLen, progress, time.Now().UnixMilli())
}

func (e *UpgradeExecutor) writeRuntimeCheckpoint(ctx context.Context, runtimeKey string, packetNo, totalPacket, chunkSize int32, offset, chunkLen, progress int, now int64) {
	status := "UPGRADING"
	if packetNo >= totalPacket {
		status = "WAIT_RESULT"
	}
	_ = e.redis.HSet(ctx, runtimeKey, map[string]interface{}{
		"offset":             strconv.Itoa(offset),
		"packetNo":           strconv.Itoa(int(packetNo)),
		"packetTime":         strconv.FormatInt(now, 10),
		"lastPacketAt":       strconv.FormatInt(now, 10),
		"progress":           strconv.Itoa(progress),
		"status":             status,
		"currentChunkLength": strconv.Itoa(chunkLen),
		"chunkSize":          strconv.Itoa(int(chunkSize)),
		"totalPacket":        strconv.Itoa(int(totalPacket)),
	}).Err()
}

func (e *UpgradeExecutor) pushUpgradeProgressIfNecessary(ctx context.Context, runtimeKey string, ack protocol.ACKMessage, packetNo, totalPacket int32, progress int) {
	if packetNo == 1 {
		e.events.Send(ctx, EventStartTime, "START_TIME", UpgradeEventMessage{
			IMEI:      ack.IMEI(),
			TaskID:    Int64Ptr(ack.Task),
			StartTime: time.Now().Format(time.RFC3339),
		})
	}
	if progress <= 0 && packetNo < totalPacket {
		return
	}
	lastProgressKey := runtimeKey + ":lastPushProgress"
	lastStr, _ := e.redis.Get(ctx, lastProgressKey).Result()
	lastProgress, _ := strconv.Atoi(lastStr)
	if lastStr == "" {
		lastProgress = -1
	}
	if progress <= lastProgress {
		return
	}
	_ = e.redis.Set(ctx, lastProgressKey, strconv.Itoa(progress), 0).Err()
	status := "UPGRADING"
	if packetNo >= totalPacket {
		status = "WAIT_RESULT"
	}
	e.events.Send(ctx, EventProgress, "PROGRESS", UpgradeEventMessage{
		IMEI:          ack.IMEI(),
		TaskID:        Int64Ptr(ack.Task),
		UpgradeStatus: status,
		Progress:      IntPtr(progress),
	})
}

func (e *UpgradeExecutor) releaseRuntimeLock(ctx context.Context, imei string, runtimeMap map[string]string, fallbackToken string) {
	token := runtimeMap["lockToken"]
	if token == "" || token == "null" {
		token = fallbackToken
	}
	if token != "" {
		e.locks.Release(ctx, imei, token)
	}
	e.locks.ClearActive(ctx, imei)
}

func resolveNextPacketNo(ack protocol.ACKMessage, currentPacketNo int32) (int32, bool) {
	if ack.ACKType == protocol.ACKTypeUpgradeRequest && currentPacketNo == 0 {
		return 1, true
	}
	if ack.ACKType == protocol.ACKTypePacket || ack.ACKType == protocol.MockDeviceBusy {
		return ack.Packet + 1, true
	}
	return 0, false
}

func buildChunk(data []byte, packetNo, totalPacket, chunkSize int32, fileSize int64) ([]byte, int, int, bool) {
	offset := int((packetNo - 1) * chunkSize)
	if offset < 0 || int64(offset) >= fileSize || offset >= len(data) {
		return nil, 0, 0, false
	}
	length := int(chunkSize)
	remaining := int(fileSize) - offset
	if remaining < length {
		length = remaining
	}
	if offset+length > len(data) {
		length = len(data) - offset
	}
	if length <= 0 {
		return nil, 0, 0, false
	}
	chunk := make([]byte, length)
	copy(chunk, data[offset:offset+length])
	progress := int((int64(packetNo) * 100) / int64(totalPacket))
	if progress > 100 {
		progress = 100
	}
	return chunk, offset, progress, true
}

func mustInt64(value string) int64 {
	parsed, _ := strconv.ParseInt(value, 10, 64)
	return parsed
}

type PlatformUpgradeRequest struct {
	TaskID              int64  `json:"taskId"`
	DeviceID            int64  `json:"deviceId"`
	IMEI                string `json:"imei"`
	LockToken           string `json:"lockToken"`
	FirmwareNameLen     byte   `json:"firmwareNameLen"`
	FirmwareName        string `json:"firmwareName"`
	FirmwareVersionLen  byte   `json:"firmwareVersionLen"`
	FirmwareVersionName string `json:"firmwareVersionName"`
	FirmwareID          int64  `json:"firmwareId"`
	ChunkSize           int    `json:"chunkSize"`
	TotalPacket         int    `json:"totalPacket"`
	FileSize            int64  `json:"fileSize"`
	MD5                 string `json:"md5"`
	BucketName          string `json:"bucketName"`
	ObjectName          string `json:"objectName"`
}

type PlatformCancelUpgradeRequest struct {
	IMEI   string `json:"imei"`
	TaskID int64  `json:"taskId"`
	Reason byte   `json:"reason"`
}
