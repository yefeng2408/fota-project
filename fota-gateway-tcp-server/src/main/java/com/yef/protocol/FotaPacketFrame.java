package com.yef.protocol;

/**
 *  协议帧对象
 * @param version
 * @param imei
 * @param timestamp
 * @param seqId
 * @param messageType
 * @param body
 * @param crc16
 */
public record FotaPacketFrame(
        byte version,
        long bodyLength,
        String imei,
        long timestamp,
        int seqId,
        byte messageType,
        byte[] body,
        int crc16) {

    public FotaPacketFrame(byte version,long bodyLength, String imei, long timestamp,
                           int seqId, byte messageType, byte[] body, int crc16) {
        this.version = version;
        this.bodyLength = bodyLength;
        this.imei = imei;
        this.timestamp = timestamp;
        this.seqId = seqId;
        this.messageType = messageType;
        this.body = body == null ? new byte[0] : body;
        this.crc16 = crc16;
    }

}
