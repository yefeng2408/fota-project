package com.yef.protocol;

/*
 * FOTA TCP 协议说明
 *
 * 一、协议结构
 * 1. 固定头（Fixed Header）
 * 2. 消息体（Body）
 * 3. 校验尾（CRC16 + Tail）
 *
 * 二、完整帧长度计算
 * 完整帧长度 = 固定头长度 + bodyLength + 校验尾长度
 *           = 25 + bodyLength + 3
 *           = 28 + bodyLength
 *
 * 三、字段定义
 * -------------------------------------------------------------------------------
 * | 协议字段              | 类型        | 长度      | 说明                              |
 * -------------------------------------------------------------------------------
 * | head                 | uint8       | 1 byte    | 包头标识符，固定为 0x5B            |
 * -------------------------------------------------------------------------------
 * | version              | uint8       | 1 byte    | 协议版本号，默认 0x01，预留扩展      |
 * | length               | uint32      | 4 byte    | 仅表示 body 的字节长度              |
 * | imei                 | byte[8]     | 8 byte    | 设备 IMEI，8 位数字字符串           |
 * | timestamp            | uint64      | 8 byte    | 报文时间戳，单位：毫秒              |
 * | seqId                | uint16      | 2 byte    | 流水号，递增，溢出后重置            |
 * | messageType          | uint8       | 1 byte    | 消息类型                          |
 * -------------------------------------------------------------------------------
 * | body                 | byte[]      | N byte    | 业务数据体，按 messageType 区分     |
 * -------------------------------------------------------------------------------
 * | crc16                | uint16      | 2 byte    | CRC16 校验值                       |
 * | tail                 | uint8       | 1 byte    | 包尾标识符，固定为 0x5D            |
 * -------------------------------------------------------------------------------
 *
 * 四、length 字段说明
 * 1. length 仅表示 body 的长度
 * 2. length 不包含固定头字段，也不包含 crc16 和 tail
 *
 * 五、CRC16 校验范围
 * CRC16 的计算范围为：
 * 从 version 字段开始，到 body 字段结束
 *
 * 即 CRC16 覆盖：
 * - version
 * - length
 * - imei
 * - timestamp
 * - seqId
 * - messageType
 * - body
 *
 * 不覆盖：
 * - head
 * - crc16 自身
 * - tail
 *
 * 六、消息方向约定
 * 1. 0x00 ~ 0x7F：设备上行消息（Device -> Platform）
 * 2. 0x80 ~ 0xFF：平台下行消息（Platform -> Device）
 */
public final class FotaProtocolConstants {

    /**
     * 包头标识符
     */
    public static final byte HEAD = 0x5B;

    /**
     * 包尾标识符
     */
    public static final byte TAIL = 0x5D;

    /**
     * 协议版本号
     */
    public static final byte VERSION = 0x01;

    // =========================
    // 设备上行消息（0x00 ~ 0x7F）
    // =========================

    /**
     * 通用 ACK 应答消息（设备 -> 平台）
     * <p>
     * body 结构：
     * - taskId   (8 byte)
     * - packetNo (4 byte)
     * - ackType  (1 byte)
     * <p>
     * 说明：
     * 1. 该消息用于设备对平台下行指令的统一确认
     * 2. 具体确认哪一种下行消息，由 ackType 区分
     * 3. 当 ackType = ACK_TYPE_UPGRADE_REQUEST 时，packetNo 可为 0
     * 4. 当 ackType = ACK_TYPE_PACKET 时，packetNo 必须是具体分包序号
     * 5. 当 ackType = ACK_TYPE_CANCEL 时，表示设备已确认取消升级指令
     */
    public static final byte MSG_ACK = 0x03;

    /**
     * FAIL 失败消息（设备 -> 平台）
     * <p>
     * body 结构：
     * - taskId    (8 byte)
     * - packetNo  (4 byte)
     * - errorCode (1 byte)
     * <p>
     * 说明：
     * 1. 用于设备侧或网关侧上报升级失败
     * 2. errorCode >= 1 表示失败原因编码
     * 3. 该消息更偏向“过程失败”上报，例如：
     * - 分包校验失败
     * - 设备侧写入异常
     * - 网关侧检测到设备连接异常等
     * <p>
     * 常见 errorCode 定义：
     * 参考该类：PacketErrorType
     */
    public static final byte MSG_FAIL = 0x04;

    /**
     * 心跳消息（设备 -> 平台）
     * <p>
     * body 结构：
     * - 无
     * <p>
     * 说明：
     * 1. 设备定时上报，用于维持在线状态
     * 2. 平台通常无需对该心跳包做业务 ACK
     * 3. 心跳消息不表示升级进度，仅表示设备仍在线
     */
    public static final byte MSG_HEARTBEAT = 0x05;

    /**
     * 升级结果上报消息（设备 -> 平台）
     * <p>
     * 说明：
     * 1. 用于设备在升级流程完成后，主动上报最终结果
     * 2. 最终结果一般包括：
     * - SUCCESS（升级成功）
     * - FAIL（升级失败）
     * 3. 该消息是平台判断本轮升级是否真正结束的重要依据
     */
    public static final byte MSG_UPGRADE_RESULT = 0x06;

    /**
     * 设备开机上线消息（设备 -> 平台）
     * <p>
     * 说明：
     * 1. 设备首次建立 TCP 连接后发送
     * 2. 用于网关识别设备上线、建立设备连接上下文
     * 3. 通常在 channelActive 后由 mock-device-client 主动发送
     */
    public static final byte MSG_DEVICE_BOOT_UP = 0x10;

    // =========================
    // 平台下行消息（0x80 ~ 0xFF）
    // =========================

    /**
     * 升级请求消息（平台 -> 设备）
     * <p>
     * 说明：
     * 1. 用户在管理平台点击“开始升级”按钮后触发
     * 2. 平台向设备发送升级元信息，例如：
     * - taskId
     * - firmwareId
     * - totalPacket
     * - chunkSize
     * - fileSize
     * - md5
     * 3. 设备收到该消息后，应回复 ACK_TYPE_UPGRADE_REQUEST
     */
    public static final byte MSG_UPGRADE_REQUEST = (byte) 0x81;

    /**
     * 固件分包消息（平台 -> 设备）
     * <p>
     * 说明：
     * 1. 平台按顺序向设备下发固件分包数据
     * 2. 每个分包通常包含：
     * - taskId
     * - packetNo
     * - totalPacket
     * - chunkLength
     * - chunkData
     * 3. 设备收到后，应回复 ACK_TYPE_PACKET
     */
    public static final byte MSG_UPGRADE_PACKET = (byte) 0x82;

    /**
     * 平台通用 ACK 确认消息（平台 -> 设备）
     * <p>
     * body 结构：
     * - taskId         (8 byte)
     * - refMessageType (1 byte)
     * - ackStatus      (1 byte)
     * - reasonCode     (1 byte)
     * <p>
     * 说明：
     * 1. 该消息用于平台对设备上行关键消息做统一确认
     * 2. 具体确认哪一种上行消息，由 refMessageType 区分
     * 3. 当 refMessageType = MSG_DEVICE_BOOT_UP 时，taskId 固定为 0 🔥
     * 4. 当 refMessageType = MSG_UPGRADE_RESULT 时，taskId 为本次升级任务ID
     * 5. ackStatus 表示平台是否已成功接收并处理该上行消息
     * 6. reasonCode 用于补充失败原因，成功场景下固定为 0
     */
    public static final byte PLATFORM_ACK = (byte) 0x83;

    /**
     * 取消升级消息（平台 -> 设备）
     * <p>
     * 说明：
     * 1. 用户在管理平台点击“取消升级”按钮后触发
     * 2. 通常要求设备当前正处于升级流程中
     * 3. 设备收到后，应回复 ACK_TYPE_CANCEL
     */
    public static final byte MSG_CANCEL_UPGRADE = (byte) 0x87;

    // =========================
    // MessageType 类型定义
    // =========================

    /**
     * 对应 MSG_UPGRADE_REQUEST（0x81）的 ACK
     */
    public static final byte ACK_TYPE_UPGRADE_REQUEST = 1;

    /**
     * 对应 MSG_UPGRADE_PACKET（0x82）的 ACK
     */
    public static final byte ACK_TYPE_PACKET = 2;

    /**
     * 对应 MSG_CANCEL_UPGRADE（0x87）的 ACK
     */
    public static final byte ACK_TYPE_CANCEL = 4;

    /**
     * 心跳 ACK 类型预留标识
     * <p>
     * 说明：
     * 1. 当前系统中，心跳包本身使用独立消息类型 MSG_HEARTBEAT = 0x05
     * 2. 当前协议下，心跳消息不走 MSG_ACK（0x03）体系，因此该常量暂不参与实际业务流程
     * 3. 若后续需要把心跳确认统一纳入 ACK 体系，可保留该类型作为扩展
     */
    public static final byte ACK_TYPE_HEARTBEAT = 5;


    /**
     * 上行消息。mock-device服务的业务线程写本地固件文件触发背压消息，服务端收到该消息后 降低packetSend的速率
     */
    public static final byte MOCK_DEVICE_BUSY = 6;


    private FotaProtocolConstants() {
    }
}