package protocol

import (
	"bytes"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"sync/atomic"
	"time"
)

var seq uint32

type Frame struct {
	Version     byte
	BodyLength  uint32
	Imei        string
	Timestamp   int64
	SeqID       uint16
	MessageType byte
	Body        []byte
	CRC16       uint16
}

func ReadFrame(r io.Reader) (*Frame, error) {
	head := make([]byte, 1)
	if _, err := io.ReadFull(r, head); err != nil {
		return nil, err
	}
	for head[0] != Head {
		if _, err := io.ReadFull(r, head); err != nil {
			return nil, err
		}
	}

	fixed := make([]byte, 24)
	if _, err := io.ReadFull(r, fixed); err != nil {
		return nil, err
	}
	bodyLength := binary.BigEndian.Uint32(fixed[1:5])
	if bodyLength > MaxBodyLength {
		return nil, fmt.Errorf("invalid body length: %d", bodyLength)
	}

	body := make([]byte, bodyLength)
	if _, err := io.ReadFull(r, body); err != nil {
		return nil, err
	}

	trailer := make([]byte, 3)
	if _, err := io.ReadFull(r, trailer); err != nil {
		return nil, err
	}
	if trailer[2] != Tail {
		return nil, fmt.Errorf("invalid frame tail: 0x%02X", trailer[2])
	}

	frame := &Frame{
		Version:     fixed[0],
		BodyLength:  bodyLength,
		Imei:        string(fixed[5:13]),
		Timestamp:   int64(binary.BigEndian.Uint64(fixed[13:21])),
		SeqID:       binary.BigEndian.Uint16(fixed[21:23]),
		MessageType: fixed[23],
		Body:        body,
		CRC16:       binary.BigEndian.Uint16(trailer[0:2]),
	}
	if frame.Version != Version {
		return nil, fmt.Errorf("unsupported version: %d", frame.Version)
	}
	if calc := CRC16(crcPayload(frame.Version, frame.BodyLength, frame.Imei, frame.Timestamp, frame.SeqID, frame.MessageType, body)); calc != frame.CRC16 {
		return nil, fmt.Errorf("crc16 mismatch, expected=%d, calculated=%d", frame.CRC16, calc)
	}
	return frame, nil
}

func DecodeMessage(frame *Frame) (Message, error) {
	body := bytes.NewReader(frame.Body)
	switch frame.MessageType {
	case MsgDeviceBootUp:
		firmwareVersion, err := readUTF8ByteString(body)
		if err != nil {
			return nil, err
		}
		deviceType, err := readUTF8ByteString(body)
		if err != nil {
			return nil, err
		}
		return DeviceBootUpMessage{Imei: frame.Imei, FirmwareVersion: firmwareVersion, DeviceType: deviceType}, nil
	case MsgHeartbeat:
		return HeartbeatMessage{Imei: frame.Imei}, nil
	case MsgACK:
		var task int64
		var packet int32
		if err := binary.Read(body, binary.BigEndian, &task); err != nil {
			return nil, err
		}
		if err := binary.Read(body, binary.BigEndian, &packet); err != nil {
			return nil, err
		}
		ackType, err := body.ReadByte()
		if err != nil {
			return nil, err
		}
		return ACKMessage{Imei: frame.Imei, Task: task, Packet: packet, ACKType: ackType}, nil
	case MsgFail:
		var task int64
		var packet int32
		if err := binary.Read(body, binary.BigEndian, &task); err != nil {
			return nil, err
		}
		if err := binary.Read(body, binary.BigEndian, &packet); err != nil {
			return nil, err
		}
		errorCode, err := body.ReadByte()
		if err != nil {
			return nil, err
		}
		return FailMessage{Imei: frame.Imei, Task: task, Packet: packet, ErrorCode: int(errorCode)}, nil
	case MsgUpgradeResult:
		var task int64
		var costTime int32
		if err := binary.Read(body, binary.BigEndian, &task); err != nil {
			return nil, err
		}
		result, err := body.ReadByte()
		if err != nil {
			return nil, err
		}
		errorCode, err := body.ReadByte()
		if err != nil {
			return nil, err
		}
		if err := binary.Read(body, binary.BigEndian, &costTime); err != nil {
			return nil, err
		}
		return UpgradeResultMessage{Imei: frame.Imei, Task: task, Result: result, ErrorCode: int(errorCode), CostTime: costTime}, nil
	default:
		return nil, fmt.Errorf("unsupported message type: 0x%02X", frame.MessageType)
	}
}

func EncodeMessage(msg Message) ([]byte, error) {
	body, err := encodeBody(msg)
	if err != nil {
		return nil, err
	}
	if len(body) > MaxBodyLength {
		return nil, fmt.Errorf("body too large: %d", len(body))
	}
	next := atomic.AddUint32(&seq, 1)
	if next > 65535 {
		atomic.StoreUint32(&seq, 1)
		next = 1
	}
	timestamp := time.Now().UnixMilli()
	messageType := msg.MessageType()
	imei := msg.IMEI()
	payload := crcPayload(Version, uint32(len(body)), imei, timestamp, uint16(next), messageType, body)
	crc := CRC16(payload)

	var out bytes.Buffer
	out.WriteByte(Head)
	out.WriteByte(Version)
	_ = binary.Write(&out, binary.BigEndian, uint32(len(body)))
	if err := writeFixedIMEI(&out, imei); err != nil {
		return nil, err
	}
	_ = binary.Write(&out, binary.BigEndian, uint64(timestamp))
	_ = binary.Write(&out, binary.BigEndian, uint16(next))
	out.WriteByte(messageType)
	out.Write(body)
	_ = binary.Write(&out, binary.BigEndian, crc)
	out.WriteByte(Tail)
	return out.Bytes(), nil
}

func encodeBody(msg Message) ([]byte, error) {
	var body bytes.Buffer
	switch m := msg.(type) {
	case PlatformCommonACK:
		_ = binary.Write(&body, binary.BigEndian, uint64(m.Task))
		body.WriteByte(m.RefMessageType)
		body.WriteByte(m.ACKStatus)
		body.WriteByte(m.ReasonCode)
	case UpgradeRequestMessage:
		if len(m.MD5) != 16 {
			return nil, fmt.Errorf("upgrade request md5 must be 16 bytes, actual=%d", len(m.MD5))
		}
		_ = binary.Write(&body, binary.BigEndian, uint64(m.Task))
		_ = binary.Write(&body, binary.BigEndian, uint64(m.FirmwareID))
		if err := writeUTF8ByteString(&body, m.FirmwareName); err != nil {
			return nil, err
		}
		if err := writeUTF8ByteString(&body, m.FirmwareVersionName); err != nil {
			return nil, err
		}
		_ = binary.Write(&body, binary.BigEndian, uint32(m.TotalPacket))
		_ = binary.Write(&body, binary.BigEndian, uint32(m.ChunkSize))
		_ = binary.Write(&body, binary.BigEndian, uint64(m.FileSize))
		body.Write(m.MD5)
	case UpgradePacketMessage:
		_ = binary.Write(&body, binary.BigEndian, uint64(m.Task))
		_ = binary.Write(&body, binary.BigEndian, uint32(m.PacketNo))
		_ = binary.Write(&body, binary.BigEndian, uint32(m.TotalPacket))
		_ = binary.Write(&body, binary.BigEndian, uint32(len(m.ChunkData)))
		body.Write(m.ChunkData)
	case ACKMessage:
		_ = binary.Write(&body, binary.BigEndian, uint64(m.Task))
		_ = binary.Write(&body, binary.BigEndian, uint32(m.Packet))
		body.WriteByte(m.ACKType)
	case CancelUpgradeMessage:
		_ = binary.Write(&body, binary.BigEndian, uint64(m.Task))
		body.WriteByte(m.Reason)
	default:
		return nil, fmt.Errorf("unsupported outbound message: %T", msg)
	}
	return body.Bytes(), nil
}

func crcPayload(version byte, bodyLength uint32, imei string, timestamp int64, seqID uint16, messageType byte, body []byte) []byte {
	var payload bytes.Buffer
	payload.WriteByte(version)
	_ = binary.Write(&payload, binary.BigEndian, bodyLength)
	_ = writeFixedIMEI(&payload, imei)
	_ = binary.Write(&payload, binary.BigEndian, uint64(timestamp))
	_ = binary.Write(&payload, binary.BigEndian, seqID)
	payload.WriteByte(messageType)
	payload.Write(body)
	return payload.Bytes()
}

func writeFixedIMEI(w io.Writer, imei string) error {
	if len(imei) != 8 {
		return fmt.Errorf("imei must be 8 ascii chars: %s", imei)
	}
	_, err := io.WriteString(w, imei)
	return err
}

func readUTF8ByteString(r *bytes.Reader) (string, error) {
	length, err := r.ReadByte()
	if err != nil {
		return "", err
	}
	if r.Len() < int(length) {
		return "", errors.New("short byte string")
	}
	buf := make([]byte, int(length))
	if _, err := io.ReadFull(r, buf); err != nil {
		return "", err
	}
	return string(buf), nil
}

func writeUTF8ByteString(w io.Writer, value string) error {
	if len([]byte(value)) > 255 {
		return errors.New("string too long for uint8 length")
	}
	if _, err := w.Write([]byte{byte(len([]byte(value)))}); err != nil {
		return err
	}
	_, err := io.WriteString(w, value)
	return err
}
