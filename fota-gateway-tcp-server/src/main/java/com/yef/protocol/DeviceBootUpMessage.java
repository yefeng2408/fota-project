package com.yef.protocol;


public record DeviceBootUpMessage(
        String imei,
        String firmwareVersion,
        String deviceType) implements FotaMessage {

    @Override
    public byte getMessageType() {
        return FotaProtocolConstants.MSG_DEVICE_BOOT_UP;
    }

    @Override
    public Long getTaskId() {
        return null;
    }

    @Override
    public String imei(){
        return imei;
    }

    @Override
    public String toString() {
        return "DeviceBootUpMessage{imei='" + imei + "', firmwareVersion='" + firmwareVersion
                + "', deviceType='" + deviceType + "'}";
    }
}
