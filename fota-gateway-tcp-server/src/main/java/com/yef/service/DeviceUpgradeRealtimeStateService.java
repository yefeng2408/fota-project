package com.yef.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@Service
public class DeviceUpgradeRealtimeStateService {

    private static final String STATUS_KEY_PREFIX = "device:upgrade:status:";
    private static final String VERSION_KEY_PREFIX = "device:upgrade:version:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public DeviceUpgradeRealtimeStateService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public Long nextVersion(Long deviceId) {
        String key = VERSION_KEY_PREFIX + deviceId;
        Long version = stringRedisTemplate.opsForValue().increment(key);
        if (version != null) {
            stringRedisTemplate.expire(key, 7, TimeUnit.DAYS);
        }
        return version;
    }

    public void saveRealtimeState(Long deviceId, RealtimeUpgradeState state) {
        String key = STATUS_KEY_PREFIX + deviceId;
        try {
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(state),
                    7,
                    TimeUnit.DAYS
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化设备实时升级状态失败", e);
        }
    }

    public RealtimeUpgradeState getRealtimeState(Long deviceId) {
        String key = STATUS_KEY_PREFIX + deviceId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RealtimeUpgradeState.class);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化设备实时升级状态失败", e);
        }
    }

    @Data
    public static class RealtimeUpgradeState {
        private String status;
        private Integer progress;
        private Integer packetNo;
        private Integer ackedPacketCount;
        private Long lastPacketAt;
        private Long version;
    }

    /**
     * Gateway 侧怎么发事件
     * 现在网关收到 ACK / 结果上报时，可以这样处理：
     *
     * 1. 首次进入 UPGRADE_REQUESTED
     * Long version = realtimeStateService.nextVersion(deviceId);
     *
     * RealtimeUpgradeState state = new RealtimeUpgradeState();
     * state.setStatus("UPGRADE_REQUESTED");
     * state.setVersion(version);
     * state.setLastPacketAt(System.currentTimeMillis());
     * realtimeStateService.saveRealtimeState(deviceId, state);
     *
     * DeviceUpgradeStatusEvent event = new DeviceUpgradeStatusEvent();
     * event.setDeviceId(deviceId);
     * event.setTaskId(taskId);
     * event.setStatus("UPGRADE_REQUESTED");
     * event.setVersion(version);
     * event.setEventTime(System.currentTimeMillis());
     *
     * mqProducer.send(event);
     *
     *
     * 2. 首次进入 UPGRADING
     *  先从 Redis 里取当前状态：
     *      RealtimeUpgradeState oldState = realtimeStateService.getRealtimeState(deviceId);
     *  如果不是 UPGRADING，说明这是第一次进入：
     *      if (oldState == null || !"UPGRADING".equals(oldState.getStatus())) {
     *     Long version = realtimeStateService.nextVersion(deviceId);
     *
     *     RealtimeUpgradeState state = new RealtimeUpgradeState();
     *     state.setStatus("UPGRADING");
     *     state.setVersion(version);
     *     state.setProgress(progress);
     *     state.setPacketNo(packetNo);
     *     state.setAckedPacketCount(packetNo);
     *     state.setLastPacketAt(System.currentTimeMillis());
     *     realtimeStateService.saveRealtimeState(deviceId, state);
     *
     *     DeviceUpgradeStatusEvent event = new DeviceUpgradeStatusEvent();
     *     event.setDeviceId(deviceId);
     *     event.setTaskId(taskId);
     *     event.setStatus("UPGRADING");
     *     event.setVersion(version);
     *     event.setEventTime(System.currentTimeMillis());
     *     event.setProgress(progress);
     *     event.setPacketNo(packetNo);
     *
     *     mqProducer.send(event);
     * } else {
     *     // 已经是 UPGRADING，只更新 Redis 进度，不发 MQ
     *     oldState.setProgress(progress);
     *     oldState.setPacketNo(packetNo);
     *     oldState.setAckedPacketCount(packetNo);
     *     oldState.setLastPacketAt(System.currentTimeMillis());
     *     realtimeStateService.saveRealtimeState(deviceId, oldState);
     * }
     *
     */
}
