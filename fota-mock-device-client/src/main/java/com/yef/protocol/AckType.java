package com.yef.protocol;

/**
 * @description: TODO
 * @author: yefeng
 * @date: 2026/05/13 16:31
 */
public final class AckType {

    /**
     * 上行消息。对应着下行0x81消息类型的ACK
      */
    public static final byte UPGRADE_REQUEST_ACK = 1;
    /**
     * 上行消息。对应着下行0x82消息类型的ACK
     */
    public static final byte PACKET_ACK = 2;
    /**
     * 上行消息。对应着下行0x87消息类型的ACK
     */
    public static final byte CANCEL_ACK = 4;
    /**
     * 上行消息。平台无需回复设备的心跳消息
     */
    public static final byte HEARTBEAT = 5;
    /**
     * 上行消息。设备侧写固件文件触发背压
     */
    public static final byte BUSY = 6;
}