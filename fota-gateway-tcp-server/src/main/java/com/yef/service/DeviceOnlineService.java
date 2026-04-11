package com.yef.service;

import io.netty.channel.Channel;
import org.springframework.stereotype.Service;

@Service
public class DeviceOnlineService {

    public Long resolveDeviceId(String imei) {
        // 后续替换为 Redis / DB 查询：imei -> deviceId。当前先用 imei 的 hash 保持流程闭环。
        if (imei == null || imei.isBlank()) {
            return null;
        }
        return Math.abs((long) imei.hashCode());
    }

    public void onDeviceOnline(String imei, Long deviceId, Channel channel) {
        System.out.println("[DeviceOnlineService] online, imei=" + imei + ", deviceId=" + deviceId
                + ", channelId=" + channel.id().asShortText());
    }

    public void onDeviceOffline(String imei, Long deviceId) {
        System.out.println("[DeviceOnlineService] offline, imei=" + imei + ", deviceId=" + deviceId);
    }
}
