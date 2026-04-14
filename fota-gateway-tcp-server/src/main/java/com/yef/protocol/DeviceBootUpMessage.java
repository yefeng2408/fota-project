package com.yef.protocol;

public class DeviceBootUpMessage implements FotaMessage {

    private final String imei;
    private final String firmwareVersion;
    private final String deviceType;

    public DeviceBootUpMessage(String imei, String firmwareVersion, String deviceType) {
        this.imei = imei;
        this.firmwareVersion = firmwareVersion;
        this.deviceType = deviceType;
    }

    @Override
    public byte getMessageType() {
        return FotaProtocolConstants.MSG_DEVICE_BOOT_UP;
    }

    @Override
    public String getImei() {
        return imei;
    }

    @Override
    public Long getTaskId() {
        return null;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public String getDeviceType() {
        return deviceType;
    }

    @Override
    public String toString() {
        return "DeviceBootUpMessage{imei='" + imei + "', firmwareVersion='" + firmwareVersion
                + "', deviceType='" + deviceType + "'}";
    }
}
