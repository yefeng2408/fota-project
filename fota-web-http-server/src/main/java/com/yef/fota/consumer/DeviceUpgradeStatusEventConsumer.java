package com.yef.fota.consumer;

import com.yef.UpgradeEventMessage;
import com.yef.fota.api.dto.DeviceUpgradeEventRequest;
import com.yef.fota.api.dto.resp.DeviceUpgradeCancelEventResult;
import com.yef.fota.api.dto.resp.DeviceUpgradeStartTimeEventResult;
import com.yef.fota.api.dto.resp.UpdateDeviceUpgradeFinalResult;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.UpgradeTaskEntity;
import com.yef.fota.mapper.DeviceMapper;
import com.yef.fota.mapper.UpgradeTaskMapper;
import com.yef.fota.service.UpgradeTaskService;
import com.yef.fota.websocket.WebSocketConfig;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @description: 消费设备网关推送的升级状态事件，负责落库与 websocket 广播
 * @author: yefeng
 * @date: 2026/04/15 01:24
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "fota.rocketmq.upgrade-event-consumer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RocketMQMessageListener(
        topic = "FOTA_UPGRADE_EVENT_TOPIC",
        consumerGroup = "fota-web-upgrade-event-consumer-group"
)
public class DeviceUpgradeStatusEventConsumer implements RocketMQListener<UpgradeEventMessage> {

    private final DeviceMapper deviceMapper;
    private final UpgradeTaskMapper upgradeTaskMapper;
    private final UpgradeTaskService upgradeTaskService;
    private final WebSocketConfig webSocketConfig;

    @Override
    public void onMessage(UpgradeEventMessage event) {
        if (event == null || !StringUtils.hasText(event.getEventType())) {
            return;
        }
        try {
            switch (event.getEventType()) {
                case UpgradeEventMessage.EventType.START_TIME:
                    handleStartTime(event);
                    break;
                case UpgradeEventMessage.EventType.UPGRADING:
                    handleUpgrading(event);
                    break;
                case UpgradeEventMessage.EventType.PROGRESS:
                    handleProgress(event);
                    break;
                case UpgradeEventMessage.EventType.FINAL_RESULT:
                    handleFinalResult(event);
                    break;
                case UpgradeEventMessage.EventType.CANCEL_RESULT:
                    handleCancelResult(event);
                    break;
                default:
                    log.warn("忽略未知升级事件类型, eventType={}, eventId={}", event.getEventType(), event.getEventId());
            }
        } catch (Exception e) {
            log.error("消费设备升级状态事件失败，触发 RocketMQ 重试, event={}", event, e);
            throw e;
        }
    }

    private void handleStartTime(UpgradeEventMessage event) {
        if (event.getTaskId() == null || event.getStartTime() == null) {
            log.warn("忽略缺少开始时间关键字段的升级事件, eventId={}, taskId={}, startTime={}",
                    event.getEventId(), event.getTaskId(), event.getStartTime());
            return;
        }
        upgradeTaskService.updateUpgradeStartTime(
                new DeviceUpgradeStartTimeEventResult(event.getTaskId(), event.getStartTime())
        );
    }

    private void handleUpgrading(UpgradeEventMessage event) {
        if (!StringUtils.hasText(event.getImei())) {
            log.warn("忽略缺少 imei 的 UPGRADING 事件, eventId={}", event.getEventId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String status = StringUtils.hasText(event.getUpgradeStatus()) ? event.getUpgradeStatus() : "UPGRADING";

        DeviceEntity device = deviceMapper.selectDeviceByImei(event.getImei());
        if (device != null) {
            device.setDeviceUpgradeStatus(status);
            device.setUpdatedAt(now);
            deviceMapper.updateById(device);
        }

        UpgradeTaskEntity task = findRelatedTask(event);
        if (task != null) {
            task.setTaskStatus(status);
            task.setUpdatedAt(now);
            upgradeTaskMapper.updateById(task);
        }

        webSocketConfig.pushDeviceUpgradeEvent(new DeviceUpgradeEventRequest(
                event.getImei(),
                status,
                null,
                null,
                null
        ));
    }

    private void handleProgress(UpgradeEventMessage event) {
        if (!StringUtils.hasText(event.getImei())) {
            log.warn("忽略缺少 imei 的 PROGRESS 事件, eventId={}", event.getEventId());
            return;
        }

        String status = event.getUpgradeStatus();
        Integer progress = event.getProgress();
        LocalDateTime now = LocalDateTime.now();

        if (shouldPersistRuntimeStatus(status) || progress != null) {
            DeviceEntity device = deviceMapper.selectDeviceByImei(event.getImei());
            if (device != null) {
                if (shouldPersistRuntimeStatus(status)) {
                    device.setDeviceUpgradeStatus(status);
                }
                device.setUpdatedAt(now);
                deviceMapper.updateById(device);
            }

            UpgradeTaskEntity task = findRelatedTask(event);
            if (task != null) {
                if (shouldPersistRuntimeStatus(status)) {
                    task.setTaskStatus(status);
                }
                if (progress != null) {
                    task.setProgress(progress);
                }
                task.setUpdatedAt(now);
                upgradeTaskMapper.updateById(task);
            }
        }

        webSocketConfig.pushDeviceUpgradeEvent(new DeviceUpgradeEventRequest(
                event.getImei(),
                status,
                progress,
                event.getCurrentFirmwareVersion(),
                event.getTargetFirmwareVersion()
        ));
    }

    private void handleFinalResult(UpgradeEventMessage event) {
        if (!StringUtils.hasText(event.getImei()) || event.getTaskId() == null) {
            log.warn("忽略缺少关键字段的 FINAL_RESULT 事件, eventId={}, imei={}, taskId={}",
                    event.getEventId(), event.getImei(), event.getTaskId());
            return;
        }

        upgradeTaskService.updateDeviceUpgradeFinalEventResult(new UpdateDeviceUpgradeFinalResult(
                event.getImei(),
                String.valueOf(event.getTaskId()),
                event.getUpgradeStatus(),
                event.getProgress(),
                event.getCurrentFirmwareVersion(),
                event.getTargetFirmwareVersion(),
                nullSafeInt(event.getCurrentPacket()),
                nullSafeInt(event.getTotalPacket()),
                event.getFailReason(),
                event.getStartTime(),
                event.getEndTime() != null ? event.getEndTime() : eventTimeToLocalDateTime(event.getEventTime())
        ));

        webSocketConfig.pushDeviceUpgradeEvent(new DeviceUpgradeEventRequest(
                event.getImei(),
                event.getUpgradeStatus(),
                event.getProgress(),
                event.getCurrentFirmwareVersion(),
                event.getTargetFirmwareVersion()
        ));
    }

    private void handleCancelResult(UpgradeEventMessage event) {
        if (!StringUtils.hasText(event.getImei())) {
            log.warn("忽略缺少 imei 的 CANCEL_RESULT 事件, eventId={}", event.getEventId());
            return;
        }

        String status = StringUtils.hasText(event.getUpgradeStatus()) ? event.getUpgradeStatus() : "CANCEL_UPGRADE";
        upgradeTaskService.updateCancelFinalEventResult(new DeviceUpgradeCancelEventResult(
                event.getImei(),
                status
        ));
        webSocketConfig.pushDeviceUpgradeCancelEvent(new DeviceUpgradeCancelEventResult(
                event.getImei(),
                status
        ));
    }

    private UpgradeTaskEntity findRelatedTask(UpgradeEventMessage event) {
        if (event.getTaskId() != null) {
            UpgradeTaskEntity task = upgradeTaskMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UpgradeTaskEntity>()
                            .eq("task_id", event.getTaskId())
                            .last("limit 1")
            );
            if (task != null) {
                return task;
            }
        }
        if (!StringUtils.hasText(event.getImei())) {
            return null;
        }
        return upgradeTaskMapper.selectUpgradingTaskByImei(event.getImei());
    }

    private boolean shouldPersistRuntimeStatus(String status) {
        return "UPGRADING".equals(status) || "WAIT_RESULT".equals(status);
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private LocalDateTime eventTimeToLocalDateTime(Long eventTime) {
        if (eventTime == null || eventTime <= 0) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofEpochSecond(eventTime / 1000, (int) (eventTime % 1000) * 1_000_000, java.time.ZoneOffset.ofHours(8));
    }
}
