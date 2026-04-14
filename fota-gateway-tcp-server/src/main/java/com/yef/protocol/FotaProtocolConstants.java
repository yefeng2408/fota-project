package com.yef.protocol;

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

    public static final byte MSG_ACK = 0x03;

    public static final byte MSG_FAIL = 0x04;

    public static final byte MSG_HEARTBEAT = 0x05;

    public static final byte MSG_UPGRADE_RESULT = 0x06;

    public static final byte MSG_DEVICE_BOOT_UP = 0x10;

    public static final byte MSG_UPGRADE_REQUEST = (byte) 0x81;

    public static final byte MSG_UPGRADE_PACKET = (byte) 0x82;

    public static final byte MSG_CANCEL_UPGRADE = (byte) 0x87;

    public static final byte ACK_TYPE_UPGRADE_REQUEST = 1;

    public static final byte ACK_TYPE_PACKET = 2;

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
