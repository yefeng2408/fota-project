package com.yef.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DeviceKeepAliveService {
    /**
     * 设备基础信息 web服务所使用的key【低频更新】
     */
    private static final String DEVICE_CACHE_KEY_PREFIX = "fota:device:";
    // 使用 ZSet 维护在线设备（score=最后心跳时间戳）
    private static final String DEVICE_ONLINE_ZSET_KEY = "fota:device:online:zset";
    // 在线判定窗口（毫秒），例如 60 秒内有心跳视为在线
    private static final long ONLINE_WINDOW_MS = 60_000L;

    private final StringRedisTemplate redisTemplate;

    public DeviceKeepAliveService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Long getDeviceId(String imei) {
        if (imei == null || imei.isBlank()) {
            return null;
        }
        Object obj = redisTemplate.opsForHash().get(DEVICE_CACHE_KEY_PREFIX + imei, "id");
        if(obj==null || "".equals(obj)){
            throw new IllegalArgumentException("设备不存在。请检查设备imei:"+imei+"是否存在！");
        }
        return Long.parseLong(String.valueOf(obj));
    }

    public void onDeviceFirstConnect(Long deviceId) {
        if (deviceId == null) return;

        long now = System.currentTimeMillis();

        redisTemplate.opsForZSet().add(DEVICE_ONLINE_ZSET_KEY, String.valueOf(deviceId), now);

        log.info("--------------------->[online-connect] deviceId={}", deviceId);
    }

    public void refreshHeartbeat(Long deviceId) {
        if (deviceId == null) return;

        long now = System.currentTimeMillis();

        redisTemplate.opsForZSet().add(DEVICE_ONLINE_ZSET_KEY, String.valueOf(deviceId), now);
    }

    /**
     * 基于inActive事件 清理离线设备
     * @param deviceId
     */
    public void onDeviceOffline(Long deviceId) {
        if (deviceId == null) return;
        redisTemplate.opsForZSet().remove(DEVICE_ONLINE_ZSET_KEY, String.valueOf(deviceId));
    }

    /**
     * 定时清理超时未上报心跳的设备【兜底】
     */
    @Scheduled(fixedDelay = 30000)
    public void cleanupOffline() {
        long now = System.currentTimeMillis();
        double threshold = now - ONLINE_WINDOW_MS;
        redisTemplate.opsForZSet().removeRangeByScore(DEVICE_ONLINE_ZSET_KEY, 0, threshold);
    }

}
