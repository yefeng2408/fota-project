package com.yef.protocol;

/*
 * 协议结构：
 * 1. 固定头（Fixed Header）
 * 2. 消息体（Body）
 * 3. 校验尾（CRC + Tail）
 * -----------------------------------------------------------------------
 * | 协议字段              | 类型         | 说明                            |
 * -----------------------------------------------------------------------
 * | head (1 byte)        | uint8       | 0x5B 包头标识符                  |
 * -----------------------------------------------------------------------
 * | version (1 byte)     | uint8       | 协议版本号（默认=1，预留扩展）      |
 * | length (4 byte)      | uint32      | body长度                        |
 * | imei (8 byte)        | byte[8]     | 设备IMEI（8位数字字符串）          |
 * | timestamp (8 byte)   | uint64      | 报文时间戳（毫秒）                |
 * | seqId (2 byte)       | uint16      | 流水号（递增，溢出后重置）          |
 * | messageType (1 byte) | uint8       | 消息类型                         |
 * -----------------------------------------------------------------------
 * | body (N byte)        | byte[]      | 业务数据体（按 messageType 区分）  |
 * -----------------------------------------------------------------------
 * | crc16 (2 byte)       | uint16      | CRC校验【从version开始到body结束】 |
 * | tail (1 byte)        | uint8       | 0x5D 包尾标识符                  |
 * -----------------------------------------------------------------------
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

    /**
     * 上行消息【设备给网关的通用应答，根据ackType类型区分】
     * ackType 类型说明：
     * ackType =1        UPGRADE_REQUEST_ACK      （上行消息。对应着下行0x81消息类型的ACK）
     * ackType =2        PACKET_ACK               （上行消息。对应着下行0x82消息类型的ACK）
     * ackType =4        CANCEL_ACK               （上行消息。对应着下行0x87消息类型的ACK）
     * ackType =5        HEARTBEAT                 (上行消息。平台无需回复设备的心跳消息)
     * 说明：
     * - packetNo 在 ackType=1（升级请求ACK）时可为0
     * - packetNo 在分包ACK时必须对应具体分包序号
     * - PACKET_LAST_ACK 用于通知平台“分包已全部接收完成”，但不代表升级成功
     *
     */
    public static final byte MSG_ACK = 0x03;

    /**
     * 上行消息【🔴FAIL失败（messageType = 0x04）】
     * taskId        (8 byte)
     * packetNo      (4 byte)
     * errorCode     (1 byte) 1以上的值（包含1）都是网关层面事件触发。如设备掉线触发 channelInactive 事件。
     */
    public static final byte MSG_FAIL = 0x04;

    /**
     * 上行消息【心跳包】。协议消息body无内容
     */
    public static final byte MSG_HEARTBEAT = 0x05;

    /**
     * 上行消息【上报升级结果】
     */
    public static final byte MSG_UPGRADE_RESULT = 0x06;
    /**
     * 上行消息【设备首次开机与网关建立tcp连接后，触发channelActive事件，发送开机包】
     */
    public static final byte MSG_DEVICE_BOOT_UP = 0x10;
    /**
     * 下行消息【用户平台下发升级请求。点击设备列表的'开始升级'按钮触发】
     */
    public static final byte MSG_UPGRADE_REQUEST = (byte) 0x81;
    /**
     * 下行消息【用户平台依次下发分包数据】
     */
    public static final byte MSG_UPGRADE_PACKET = (byte) 0x82;
    /**
     * 下行消息【用户平台，点击设备列表的'取消升级'按钮触发。
     * 注意：设备表升级状态device_upgrade_status必须是处于UPGRADING状态才能点击该按钮】
     */
    public static final byte MSG_CANCEL_UPGRADE = (byte) 0x87;
    /**
     * 上行消息【对应着平台下行0x81消息类型的ACK】
     */
    public static final byte ACK_TYPE_UPGRADE_REQUEST = 1;
    /**
     * 上行消息【对应着下行0x82消息类型的ACK】
     */
    public static final byte ACK_TYPE_PACKET = 2;
    /**
     * 上行消息【对应着下行0x87消息类型的ACK】
     */
    public static final byte ACK_TYPE_CANCEL = 4;

    public static final byte ACK_TYPE_HEARTBEAT = 5;

    /**
     * 最大包长度限制
     */
    public static final int MAX_BODY_LENGTH = 1024 * 1024;

    /**
     * 最小包长度限制
     */
    public static final int FRAME_MIN_LENGTH = 28;

    private FotaProtocolConstants() {
    }
}
