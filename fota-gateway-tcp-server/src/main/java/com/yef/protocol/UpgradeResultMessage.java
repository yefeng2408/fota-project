package com.yef.protocol;

public class UpgradeResultMessage implements FotaMessage {

    private final String imei;
    private final long taskId;
    private final byte result;
    private final int errorCode;
    private final int costTime;

    public UpgradeResultMessage(String imei, long taskId, byte result, int errorCode, int costTime) {
        this.imei = imei;
        this.taskId = taskId;
        this.result = result;
        this.errorCode = errorCode;
        this.costTime = costTime;
    }

    @Override
    public byte getMessageType() {
        return FotaProtocolConstants.MSG_UPGRADE_RESULT;
    }

    @Override
    public String imei() {
        return imei;
    }

    @Override
    public Long getTaskId() {
        return taskId;
    }

    public byte getResult() {
        return result;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getCostTime() {
        return costTime;
    }

    @Override
    public String toString() {
        return "UpgradeResultMessage{imei='" + imei + "', taskId=" + taskId
                + ", result=" + result + ", errorCode=" + errorCode + ", costTime=" + costTime + "}";
    }
}
