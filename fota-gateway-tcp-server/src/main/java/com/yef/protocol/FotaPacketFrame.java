package com.yef.protocol;

public class FotaPacketFrame {

    private final byte version;
    private final String imei;
    private final long timestamp;
    private final int seqId;
    private final byte messageType;
    private final byte[] body;
    private final int crc16;

    public FotaPacketFrame(byte version, String imei, long timestamp, int seqId, byte messageType, byte[] body, int crc16) {
        this.version = version;
        this.imei = imei;
        this.timestamp = timestamp;
        this.seqId = seqId;
        this.messageType = messageType;
        this.body = body == null ? new byte[0] : body;
        this.crc16 = crc16;
    }

    public byte getVersion() {
        return version;
    }

    public String getImei() {
        return imei;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getSeqId() {
        return seqId;
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
