package com.yef.service;

import io.netty.channel.Channel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DeviceOnlineService {

    private static final String DEVICE_ONLINE_KEY_PREFIX = "fota:device:online:";
    private final StringRedisTemplate redisTemplate;

    public DeviceOnlineService( StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Long resolveDeviceId(String imei) {
        // 后续替换为 Redis / DB 查询：imei -> deviceId。当前先用 imei 的 hash 保持流程闭环。
        if (imei == null || imei.isBlank()) {
            return null;
        }
        return Math.abs((long) imei.hashCode());
    }

    public void onDeviceOnline(String imei, Channel channel) {

        redisTemplate.opsForValue().set(DEVICE_ONLINE_KEY_PREFIX + imei, "1");
        System.out.println("[DeviceOnlineService] online, imei=" + imei + ", channelId=" + channel.id().asShortText());
    }

    public void onDeviceOffline(String imei) {

        redisTemplate.opsForValue().set(DEVICE_ONLINE_KEY_PREFIX + imei, "0");
        System.out.println("[DeviceOnlineService] offline, imei=" + imei);
    }
}
