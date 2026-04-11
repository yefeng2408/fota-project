package com.yef.protocol;

public final class FotaProtocolConstants {

    public static final byte HEAD = 0x5B;
    public static final byte TAIL = 0x5D;
    public static final byte VERSION = 0x01;

    public static final byte MSG_UPGRADE_REQUEST = 0x01;
    public static final byte MSG_UPGRADE_PACKET = 0x02;
    public static final byte MSG_ACK = 0x03;
    public static final byte MSG_FAIL = 0x04;
    public static final byte MSG_DEVICE_REGISTER = 0x10;

    public static final int MAX_BODY_LENGTH = 1024 * 1024;
    public static final int FRAME_MIN_LENGTH = 10;

    private FotaProtocolConstants() {
    }
}
