package com.yef.fota.service;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DeviceOnlineService {

    // 使用 ZSet 维护在线设备（score=最后心跳时间戳）
    private static final String DEVICE_ONLINE_ZSET_KEY = "fota:device:online:zset";
    // 在线判定窗口（毫秒），例如 180 秒内有心跳视为在线
    public static final long ONLINE_WINDOW_MS = 90_000L * 2;

    private final StringRedisTemplate redisTemplate;

    public DeviceOnlineService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 统计在线设备数量（最近 ONLINE_WINDOW_MS 内有心跳）
     */
    public Long countOnline() {
        long now = System.currentTimeMillis();
        Double min = (double) (now - ONLINE_WINDOW_MS);
        Double max = (double) now;
        return redisTemplate.opsForZSet().count(DEVICE_ONLINE_ZSET_KEY, min, max);
    }


}
