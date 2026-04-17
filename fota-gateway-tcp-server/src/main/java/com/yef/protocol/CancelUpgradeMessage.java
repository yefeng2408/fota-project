package com.yef.protocol;

public class CancelUpgradeMessage implements FotaMessage {

    private final String imei;
    private final long taskId;
    private final byte reason;

    public CancelUpgradeMessage(String imei, long taskId, byte reason) {
        this.imei = imei;
        this.taskId = taskId;
        this.reason = reason;
    }

    @Override
    public byte getMessageType() {
        return FotaProtocolConstants.MSG_CANCEL_UPGRADE;
    }

    @Override
    public String imei() {
        return imei;
    }

    @Override
    public Long getTaskId() {
        return taskId;
    }

    public byte getReason() {
        return reason;
    }
}
