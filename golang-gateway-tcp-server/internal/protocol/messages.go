package protocol

type Message interface {
	MessageType() byte
	IMEI() string
	TaskID() *int64
}

type DeviceBootUpMessage struct {
	Imei            string
	FirmwareVersion string
	DeviceType      string
}

func (m DeviceBootUpMessage) MessageType() byte { return MsgDeviceBootUp }
func (m DeviceBootUpMessage) IMEI() string      { return m.Imei }
func (m DeviceBootUpMessage) TaskID() *int64    { return nil }

type HeartbeatMessage struct {
	Imei string
}

func (m HeartbeatMessage) MessageType() byte { return MsgHeartbeat }
func (m HeartbeatMessage) IMEI() string      { return m.Imei }
func (m HeartbeatMessage) TaskID() *int64    { return nil }

type ACKMessage struct {
	Imei    string
	Task    int64
	Packet  int32
	ACKType byte
}

func (m ACKMessage) MessageType() byte { return MsgACK }
func (m ACKMessage) IMEI() string      { return m.Imei }
func (m ACKMessage) TaskID() *int64    { return &m.Task }

type FailMessage struct {
	Imei      string
	Task      int64
	Packet    int32
	ErrorCode int
}

func (m FailMessage) MessageType() byte { return MsgFail }
func (m FailMessage) IMEI() string      { return m.Imei }
func (m FailMessage) TaskID() *int64    { return &m.Task }

type UpgradeResultMessage struct {
	Imei      string
	Task      int64
	Result    byte
	ErrorCode int
	CostTime  int32
}

func (m UpgradeResultMessage) MessageType() byte { return MsgUpgradeResult }
func (m UpgradeResultMessage) IMEI() string      { return m.Imei }
func (m UpgradeResultMessage) TaskID() *int64    { return &m.Task }

type PlatformCommonACK struct {
	Imei           string
	Task           int64
	RefMessageType byte
	ACKStatus      byte
	ReasonCode     byte
}

func (m PlatformCommonACK) MessageType() byte { return MsgPlatformACK }
func (m PlatformCommonACK) IMEI() string      { return m.Imei }
func (m PlatformCommonACK) TaskID() *int64    { return &m.Task }

type UpgradeRequestMessage struct {
	Imei                string
	Task                int64
	FirmwareID          int64
	FirmwareName        string
	FirmwareVersionName string
	TotalPacket         int32
	ChunkSize           int32
	FileSize            int64
	MD5                 []byte
}

func (m UpgradeRequestMessage) MessageType() byte { return MsgUpgradeRequest }
func (m UpgradeRequestMessage) IMEI() string      { return m.Imei }
func (m UpgradeRequestMessage) TaskID() *int64    { return &m.Task }

type UpgradePacketMessage struct {
	Imei        string
	Task        int64
	PacketNo    int32
	TotalPacket int32
	ChunkData   []byte
}

func (m UpgradePacketMessage) MessageType() byte { return MsgUpgradePacket }
func (m UpgradePacketMessage) IMEI() string      { return m.Imei }
func (m UpgradePacketMessage) TaskID() *int64    { return &m.Task }

type CancelUpgradeMessage struct {
	Imei   string
	Task   int64
	Reason byte
}

func (m CancelUpgradeMessage) MessageType() byte { return MsgCancelUpgrade }
func (m CancelUpgradeMessage) IMEI() string      { return m.Imei }
func (m CancelUpgradeMessage) TaskID() *int64    { return &m.Task }
