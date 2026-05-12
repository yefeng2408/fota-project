package com.yef.protocol;

public record HeartbeatMessage(String imei) implements FotaMessage {

    @Override
    public byte getMessageType() {
        return FotaProtocolConstants.MSG_HEARTBEAT;
    }

    @Override
    public Long getTaskId() {
        return null;
    }

    @Override
    public String toString() {
        return "HeartbeatMessage{imei='" + imei + "'}";
    }
}
