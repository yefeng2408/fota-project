package protocol

import (
	"bytes"
	"testing"
)

func TestEncodeDecodePlatformACKFrame(t *testing.T) {
	msg := PlatformCommonACK{
		Imei:           "12345678",
		Task:           0,
		RefMessageType: MsgDeviceBootUp,
		ACKStatus:      0,
		ReasonCode:     0,
	}

	encoded, err := EncodeMessage(msg)
	if err != nil {
		t.Fatal(err)
	}
	frame, err := ReadFrame(bytes.NewReader(encoded))
	if err != nil {
		t.Fatal(err)
	}
	if frame.Imei != "12345678" || frame.MessageType != MsgPlatformACK || frame.BodyLength != 11 {
		t.Fatalf("unexpected frame: %+v", frame)
	}
}

func TestDecodeDeviceBootUp(t *testing.T) {
	body := []byte{3, '1', '.', '0', 4, 'd', 'e', 'v', 'A'}
	frame := &Frame{
		Version:     Version,
		BodyLength:  uint32(len(body)),
		Imei:        "12345678",
		Timestamp:   1,
		SeqID:       1,
		MessageType: MsgDeviceBootUp,
		Body:        body,
	}

	msg, err := DecodeMessage(frame)
	if err != nil {
		t.Fatal(err)
	}
	boot, ok := msg.(DeviceBootUpMessage)
	if !ok {
		t.Fatalf("unexpected msg type %T", msg)
	}
	if boot.FirmwareVersion != "1.0" || boot.DeviceType != "devA" {
		t.Fatalf("unexpected boot message: %+v", boot)
	}
}
