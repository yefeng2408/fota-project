package service

import (
	"context"
	"encoding/json"
	"log"
	"time"

	"github.com/apache/rocketmq-client-go/v2"
	"github.com/apache/rocketmq-client-go/v2/primitive"
	"github.com/apache/rocketmq-client-go/v2/producer"
	"github.com/google/uuid"

	"golang-gateway-tcp-server/internal/config"
)

const (
	EventStartTime   = "START_TIME"
	EventUpgrading   = "UPGRADING"
	EventDisconnect  = "DISCONNECT"
	EventProgress    = "PROGRESS"
	EventFinalResult = "FINAL_RESULT"
	EventCancel      = "CANCEL_RESULT"
)

type UpgradeEventMessage struct {
	EventID                string `json:"eventId,omitempty"`
	EventType              string `json:"eventType,omitempty"`
	IMEI                   string `json:"imei,omitempty"`
	TaskID                 *int64 `json:"taskId,omitempty"`
	UpgradeStatus          string `json:"upgradeStatus,omitempty"`
	Version                *int64 `json:"version,omitempty"`
	Progress               *int   `json:"progress,omitempty"`
	StartTime              string `json:"startTime,omitempty"`
	EndTime                string `json:"endTime,omitempty"`
	CurrentPacket          *int   `json:"currentPacket,omitempty"`
	TotalPacket            *int   `json:"totalPacket,omitempty"`
	CurrentFirmwareVersion string `json:"currentFirmwareVersion,omitempty"`
	TargetFirmwareVersion  string `json:"targetFirmwareVersion,omitempty"`
	ErrorCode              string `json:"errorCode,omitempty"`
	FailReason             string `json:"failReason,omitempty"`
	EventTime              int64  `json:"eventTime,omitempty"`
}

type EventProducer struct {
	topic    string
	producer rocketmq.Producer
}

func NewEventProducer(cfg config.Config) (*EventProducer, error) {
	p, err := rocketmq.NewProducer(
		producer.WithNameServer([]string{cfg.RocketMQNameServer}),
		producer.WithGroupName(cfg.RocketMQGroup),
	)
	if err != nil {
		return nil, err
	}
	if err := p.Start(); err != nil {
		return nil, err
	}
	return &EventProducer{topic: cfg.RocketMQTopic, producer: p}, nil
}

func (p *EventProducer) Shutdown() {
	if p != nil && p.producer != nil {
		if err := p.producer.Shutdown(); err != nil {
			log.Printf("rocketmq shutdown failed: %v", err)
		}
	}
}

func (p *EventProducer) Send(ctx context.Context, eventType, tag string, msg UpgradeEventMessage) {
	if p == nil || p.producer == nil {
		return
	}
	msg.EventID = uuid.NewString()
	msg.EventType = eventType
	msg.EventTime = time.Now().UnixMilli()

	body, err := json.Marshal(msg)
	if err != nil {
		log.Printf("marshal upgrade event failed: %v", err)
		return
	}
	mqMsg := primitive.NewMessage(p.topic, body)
	mqMsg.WithTag(tag)
	err = p.producer.SendAsync(ctx, func(ctx context.Context, result *primitive.SendResult, err error) {
		if err != nil {
			log.Printf("MQ发送失败, eventId=%s err=%v", msg.EventID, err)
			return
		}
		log.Printf("MQ发送成功, eventId=%s msgId=%s", msg.EventID, result.MsgID)
	}, mqMsg)
	if err != nil {
		log.Printf("MQ发送提交失败, eventId=%s err=%v", msg.EventID, err)
	}
}

func IntPtr(v int) *int       { return &v }
func Int64Ptr(v int64) *int64 { return &v }
