package com.yef.session;

import io.netty.channel.Channel;

public class DeviceSession {

    /**
     * 终端 IMEI号
     */
    private String imei;

    /**
     * 平台内部设备ID，后续由 Redis / DB 映射 imei -> deviceId。
     */
    private Long deviceId;

    /**
     * 当前终端连接对应的 Netty Channel
     */
    private Channel channel;

    /**
     * 建立连接时间
     */
    private long connectTime;

    /**
     * 最后活跃时间
     */
    private long lastActiveTime;

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public long getConnectTime() {
        return connectTime;
    }

    public void setConnectTime(long connectTime) {
        this.connectTime = connectTime;
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(long lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    @Override
    public String toString() {
        return "DeviceSession{imei='" + imei + "', deviceId=" + deviceId
                + ", channel=" + channel
                + ", connectTime=" + connectTime
                + ", lastActiveTime=" + lastActiveTime + "}";
    }
}
