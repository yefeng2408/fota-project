package com.yef.protocol;

public class FailMessage implements FotaMessage {

    private final String imei;
    private final long taskId;
    private final int packetNo;
    private final int errorCode;

    public FailMessage(String imei, long taskId, int packetNo, int errorCode) {
        this.imei = imei;
        this.taskId = taskId;
        this.packetNo = packetNo;
        this.errorCode = errorCode;
    }

    @Override
    public byte getMessageType() {
        return FotaProtocolConstants.MSG_FAIL;
    }

    @Override
    public String imei() {
        return imei;
    }

    @Override
    public Long getTaskId() {
        return taskId;
    }

    public int getPacketNo() {
        return packetNo;
    }

    public int getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "FailMessage{imei='" + imei + "', taskId=" + taskId + ", packetNo=" + packetNo
                + ", errorCode=" + errorCode + "}";
    }
}
