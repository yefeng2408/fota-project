package com.yef.protocol;

import lombok.Data;

@Data
public class CancelUpgradeMessage implements FotaMessage {

    private String imei;
    private long taskId;
    private byte reason;

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


}
