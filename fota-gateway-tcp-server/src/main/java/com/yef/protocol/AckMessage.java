package com.yef.protocol;

public class AckMessage implements FotaMessage {

    private final String imei;
    private final long taskId;
    private final int packetNo;
    private final byte status;

    public AckMessage(String imei, long taskId, int packetNo, byte status) {
        this.imei = imei;
        this.taskId = taskId;
        this.packetNo = packetNo;
        this.status = status;
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

    public byte getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "AckMessage{imei='" + imei + "', taskId=" + taskId + ", packetNo=" + packetNo
                + ", status=" + status + "}";
    }
}
