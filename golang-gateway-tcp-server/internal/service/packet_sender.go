package service

import (
	"log"

	"golang-gateway-tcp-server/internal/protocol"
	"golang-gateway-tcp-server/internal/session"
)

type PacketSender struct {
	sessions *session.Manager
}

func NewPacketSender(sessions *session.Manager) *PacketSender {
	return &PacketSender{sessions: sessions}
}

func (s *PacketSender) SendToDevice(imei string, msg protocol.Message) bool {
	deviceSession := s.sessions.GetByIMEI(imei)
	if deviceSession == nil || deviceSession.Conn == nil {
		log.Printf("[PacketSender] device offline, imei=%s", imei)
		return false
	}
	frame, err := protocol.EncodeMessage(msg)
	if err != nil {
		log.Printf("encode outbound message failed, imei=%s err=%v", imei, err)
		return false
	}
	if _, err := deviceSession.Conn.Write(frame); err != nil {
		log.Printf("write outbound message failed, imei=%s err=%v", imei, err)
		return false
	}
	return true
}
