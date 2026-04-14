package com.yef.protocol;

public class HeartbeatMessage implements FotaMessage {

    private final String imei;

    public HeartbeatMessage(String imei) {
        this.imei = imei;
    }

    @Override
    public byte getMessageType() {
        return FotaProtocolConstants.MSG_HEARTBEAT;
    }

    @Override
    public String getImei() {
        return imei;
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
