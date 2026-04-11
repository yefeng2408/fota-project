package com.yef.protocol;

public class FotaPacketFrame {

    private final byte version;
    private final byte messageType;
    private final byte[] body;
    private final int crc16;

    public FotaPacketFrame(byte version, byte messageType, byte[] body, int crc16) {
        this.version = version;
        this.messageType = messageType;
        this.body = body == null ? new byte[0] : body;
        this.crc16 = crc16;
    }

    public byte getVersion() {
        return version;
    }

    public byte getMessageType() {
        return messageType;
    }

    public byte[] getBody() {
        return body;
    }

    public int getCrc16() {
        return crc16;
    }
}
