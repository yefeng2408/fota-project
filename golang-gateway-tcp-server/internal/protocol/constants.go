package protocol

const (
	Head    byte = 0x5B
	Tail    byte = 0x5D
	Version byte = 0x01

	MsgACK           byte = 0x03
	MsgFail          byte = 0x04
	MsgHeartbeat     byte = 0x05
	MsgUpgradeResult byte = 0x06
	MsgDeviceBootUp  byte = 0x10

	MsgUpgradeRequest byte = 0x81
	MsgUpgradePacket  byte = 0x82
	MsgPlatformACK    byte = 0x83
	MsgCancelUpgrade  byte = 0x87

	ACKTypeUpgradeRequest byte = 1
	ACKTypePacket         byte = 2
	ACKTypeCancel         byte = 4
	ACKTypeHeartbeat      byte = 5
	MockDeviceBusy        byte = 6

	FrameMinLength    = 28
	MaxBodyLength     = 1024 * 1024
	MaxFrameLength    = FrameMinLength + MaxBodyLength
	LengthFieldOffset = 2
	LengthFieldLength = 4
	LengthAdjustment  = 8 + 8 + 2 + 1 + 2 + 1
)
