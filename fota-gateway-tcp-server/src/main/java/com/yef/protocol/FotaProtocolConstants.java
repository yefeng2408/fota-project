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

    /**
     * 收到平台下发的升级请求指令
     */
    public static final byte MSG_UPGRADE_REQUEST = 0x01;

    /**
     * 平台下发带有固件分包的数据
     */
    public static final byte MSG_UPGRADE_PACKET = 0x02;

    /**
     *  TODO ========
     */
    public static final byte MSG_ACK = 0x03;

    /**
     *
     */
    public static final byte MSG_FAIL = 0x04;

    /**
     * 设备开机后注册
     */
    public static final byte MSG_DEVICE_REGISTER = 0x10;

    /**
     * 最大包长度限制
     */
    public static final int MAX_BODY_LENGTH = 1024 * 1024;

    /**
     * 最小包长度限制
     */
    public static final int FRAME_MIN_LENGTH = 10;

    private FotaProtocolConstants() {
    }
}
