package com.yef.protocol;

public class AckMessage implements FotaMessage {

    private final String imei;
    private final long taskId;
    private final int packetNo;
    private final byte ackType;

    public AckMessage(String imei, long taskId, int packetNo, byte ackType) {
        this.imei = imei;
        this.taskId = taskId;
        this.packetNo = packetNo;
        this.ackType = ackType;
    }

    @Override
    public byte getMessageType() {
        return FotaProtocolConstants.MSG_ACK;
    }

    @Override
    public String getImei() {
        return imei;
    }

    @Override
    public Long getTaskId() {
        return taskId;
    }

    public int getPacketNo() {
        return packetNo;
    }

    public byte getAckType() {
        return ackType;
    }

    @Override
    public String toString() {
        return "AckMessage{imei='" + imei + "', taskId=" + taskId + ", packetNo=" + packetNo
                + ", ackType=" + ackType + "}";
    }
}
