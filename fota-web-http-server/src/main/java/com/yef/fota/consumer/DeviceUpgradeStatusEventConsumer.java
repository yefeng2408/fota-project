package com.yef.fota.consumer;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yef.UpgradeEventMessage;
import com.yef.fota.api.dto.DeviceUpgradeEventRequest;
import com.yef.fota.api.dto.resp.UpgradeCancelEventResult;
import com.yef.fota.api.dto.resp.UpgradeStartTimeEventResult;
import com.yef.fota.api.dto.resp.UpdateDeviceUpgradeFinalResult;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.UpgradeTaskEntity;
import com.yef.fota.mapper.DeviceMapper;
import com.yef.fota.mapper.UpgradeTaskMapper;
import com.yef.fota.redis.semaphore.UpgradeSemaphoreService;
import com.yef.fota.service.UpgradeTaskService;
import com.yef.fota.websocket.WebSocketConfig;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private final UpgradeSemaphoreService semaphore;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String EVENT_DONE_KEY_PREFIX = "fota:mq:event:done:";
    //幂等消费的key
    private static final String EVENT_PROCESSING_KEY_PREFIX = "fota:mq:event:processing:";
    //
    private static final String SEMAPHORE_RELEASED_KEY_PREFIX = "fota:upgrade:semaphore:released:";
    private static final long EVENT_DONE_TTL_DAYS = 1;
    private static final long EVENT_PROCESSING_TTL_MINUTES = 5;
    private static final long SEMAPHORE_RELEASED_TTL_DAYS = 1;

    @Override
    public void onMessage(UpgradeEventMessage event) {
        if (event == null || !StringUtils.hasText(event.getEventType())) {
            return;
        }

        String eventId = event.getEventId();
        boolean needIdempotent = StringUtils.hasText(eventId);
        String doneKey = needIdempotent ? EVENT_DONE_KEY_PREFIX + eventId : null;
        String processingKey = needIdempotent ? EVENT_PROCESSING_KEY_PREFIX + eventId : null;

        if (needIdempotent && Boolean.TRUE.equals(stringRedisTemplate.hasKey(doneKey))) {
            log.info("忽略重复升级状态流转事件, eventId={}, eventType={}, imei={}, taskId={}",
                    eventId, event.getEventType(), event.getImei(), event.getTaskId());
            return;
        }

        if (needIdempotent) {
            //借助redis string set nx 实现幂等消费
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                    processingKey,
                    "1",
                    EVENT_PROCESSING_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
            if (!Boolean.TRUE.equals(locked)) {
                log.warn("升级事件正在被其他消费者处理，本次忽略, eventId={}, eventType={}, imei={}, taskId={}",
                        eventId, event.getEventType(), event.getImei(), event.getTaskId());
                return;
            }
        } else {
            log.warn("升级事件缺少 eventId，无法做 MQ 幂等保护, eventType={}, imei={}, taskId={}",
                    event.getEventType(), event.getImei(), event.getTaskId());
        }

        try {
            switch (event.getEventType()) {
                case UpgradeEventMessage.EventType.START_TIME:
                    handleStartTime(event);
                    break;
                case UpgradeEventMessage.EventType.UPGRADING:
                    handleUpgrading(event);
                    break;
                case UpgradeEventMessage.EventType.DISCONNECT:
                    handleDisconnect(event);
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

            if (needIdempotent) {
                stringRedisTemplate.opsForValue().set(doneKey, "1", EVENT_DONE_TTL_DAYS, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            if (needIdempotent) {
                stringRedisTemplate.delete(processingKey);
            }
            log.error("消费设备升级状态事件失败，触发 RocketMQ 重试, event={}", event, e);
            throw e;
        } finally {
            if (needIdempotent) {
                stringRedisTemplate.delete(processingKey);
            }
        }
    }

    private void handleDisconnect(UpgradeEventMessage event) {
        if (!StringUtils.hasText(event.getImei())) {
            log.warn("忽略缺少 imei 的 DISCONNECT 事件, eventId={}", event.getEventId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String status = StringUtils.hasText(event.getUpgradeStatus()) ? event.getUpgradeStatus() : "UPGRADING";

        UpgradeTaskEntity task = findRelatedTask(event);

        DeviceEntity device = deviceMapper.selectDeviceByImei(event.getImei());
        if (device != null) {
            device.setDeviceUpgradeStatus(status);
            device.setUpdatedAt(now);
            deviceMapper.updateById(device);
        }

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

    private void handleStartTime(UpgradeEventMessage event) {
        if (event.getTaskId() == null || event.getStartTime() == null) {
            log.warn("忽略缺少开始时间关键字段的升级事件, eventId={}, taskId={}, startTime={}",
                    event.getEventId(), event.getTaskId(), event.getStartTime());
            return;
        }
        upgradeTaskService.updateUpgradeStartTime(
                new UpgradeStartTimeEventResult(event.getTaskId(), event.getStartTime())
        );
    }

    private void handleUpgrading(UpgradeEventMessage event) {
        if (!StringUtils.hasText(event.getImei())) {
            log.warn("忽略缺少 imei 的 UPGRADING 事件, eventId={}", event.getEventId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String status = StringUtils.hasText(event.getUpgradeStatus()) ? event.getUpgradeStatus() : "UPGRADING";

        UpgradeTaskEntity task = findRelatedTask(event);
        if (task != null && !canApplyStatus(task.getTaskStatus(), status)) {
            log.warn("丢弃非法升级状态流转事件, eventId={}, imei={}, taskId={}, currentStatus={}, incomingStatus={}",
                    event.getEventId(), event.getImei(), event.getTaskId(), task.getTaskStatus(), status);
            return;
        }

        DeviceEntity device = deviceMapper.selectDeviceByImei(event.getImei());
        if (device != null) {
            device.setDeviceUpgradeStatus(status);
            device.setUpdatedAt(now);
            deviceMapper.updateById(device);
        }

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
            UpgradeTaskEntity task = findRelatedTask(event);
            if (task != null) {
                if (shouldPersistRuntimeStatus(status) && !canApplyStatus(task.getTaskStatus(), status)) {
                    log.warn("丢弃非法升级进度状态流转事件, eventId={}, imei={}, taskId={}, currentStatus={}, incomingStatus={}, progress={}",
                            event.getEventId(), event.getImei(), event.getTaskId(), task.getTaskStatus(), status, progress);
                    return;
                }
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
        UpgradeTaskEntity task = findRelatedTask(event);
        if (task != null && !canApplyStatus(task.getTaskStatus(), event.getUpgradeStatus())) {
            log.warn("丢弃非法升级最终状态流转事件, eventId={}, imei={}, taskId={}, currentStatus={}, incomingStatus={}",
                    event.getEventId(), event.getImei(), event.getTaskId(), task.getTaskStatus(), event.getUpgradeStatus());
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
        //释放信号量。获取信号量和释放信号量都是由同一个服务去操作
        releaseSemaphoreOnce(event);
        log.debug("------>升级完成 DeviceUpgradeStatusEventConsumer|handleFinalResult:{}", JSON.toJSONString(event));
    }

    private void handleCancelResult(UpgradeEventMessage event) {
        if (!StringUtils.hasText(event.getImei())) {
            log.warn("忽略缺少 imei 的 CANCEL_RESULT 事件, eventId={}", event.getEventId());
            return;
        }

        String status = StringUtils.hasText(event.getUpgradeStatus()) ? event.getUpgradeStatus() : "CANCEL_UPGRADE";
        UpgradeTaskEntity task = findRelatedTask(event);
        if (task != null && !canApplyStatus(task.getTaskStatus(), status)) {
            log.warn("丢弃非法取消升级状态流转事件, eventId={}, imei={}, taskId={}, currentStatus={}, incomingStatus={}",
                    event.getEventId(), event.getImei(), event.getTaskId(), task.getTaskStatus(), status);
            return;
        }
        upgradeTaskService.updateCancelFinalEventResult(new UpgradeCancelEventResult(
                event.getImei(),
                status
        ));
        webSocketConfig.pushDeviceUpgradeCancelEvent(new UpgradeCancelEventResult(
                event.getImei(),
                status
        ));
        //释放信号量
        releaseSemaphoreOnce(event);
    }

    /**
     * 终态事件释放分布式信号量的一次性保护。
     *
     * eventId 只能防同一条 MQ 消息重复投递；如果网关因为重试生成了不同 eventId 的 SUCCESS/FAIL/CANCEL 终态事件，
     * 仍可能重复释放信号量。所以这里用 taskId 优先，其次 imei，做业务维度的一次性释放保护。
     */
    private void releaseSemaphoreOnce(UpgradeEventMessage event) {
        String bizKey;
        if (event.getTaskId() != null) {
            bizKey = String.valueOf(event.getTaskId());
        } else if (StringUtils.hasText(event.getImei())) {
            bizKey = event.getImei();
        } else {
            log.warn("释放升级信号量失败，缺少 taskId 和 imei, eventId={}", event.getEventId());
            return;
        }

        String releaseKey = SEMAPHORE_RELEASED_KEY_PREFIX + bizKey;
        Boolean firstRelease = stringRedisTemplate.opsForValue().setIfAbsent(
                releaseKey,
                "1",
                SEMAPHORE_RELEASED_TTL_DAYS,
                TimeUnit.DAYS
        );

        if (!Boolean.TRUE.equals(firstRelease)) {
            log.info("忽略重复释放升级信号量, eventId={}, taskId={}, imei={}",
                    event.getEventId(), event.getTaskId(), event.getImei());
            return;
        }

        semaphore.release(event.getImei());
        log.info("升级信号量释放成功, eventId={}, taskId={}, imei={}", event.getEventId(), event.getTaskId(), event.getImei());
    }

    private UpgradeTaskEntity findRelatedTask(UpgradeEventMessage event) {
        if (event.getTaskId() != null) {
            UpgradeTaskEntity task = upgradeTaskMapper.selectOne(
                    new QueryWrapper<UpgradeTaskEntity>()
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

    /**
     * MQ 可能重复、乱序投递。这里按升级状态机做兜底保护，防止 SUCCESS 后又被 PROGRESS / UPGRADING 回写。
     * 主链路：WAITING/RETRY_WAITING -> UPGRADE_REQUESTED -> UPGRADING -> WAIT_RESULT -> SUCCESS/FAIL
     */
    private boolean canApplyStatus(String currentStatusFromDB, String incomingStatus) {
        if (!StringUtils.hasText(incomingStatus)) {
            return true;
        }
        if (!StringUtils.hasText(currentStatusFromDB)) {
            return true;
        }
        if (incomingStatus.equals(currentStatusFromDB)) {
            return true;
        }

        // 断点续传恢复后，允许恢复态回到正常升级态
        if ("RESUME_UPGRADING".equals(currentStatusFromDB) || "DISCONNECT".equals(currentStatusFromDB)
                && "UPGRADING".equals(incomingStatus)) {
            return true;
        }

        int currentOrderFromDB = statusOrder(currentStatusFromDB);
        int incomingOrder = statusOrder(incomingStatus);

        // 未知状态保守放行，避免因为新增状态导致消息全部被误丢。
        if (currentOrderFromDB < 0 || incomingOrder < 0) {
            log.warn("遇到未知升级状态，保守放行, currentStatusFromDB={}, incomingStatus={}", currentStatusFromDB, incomingStatus);
            return true;
        }

        // 终态不能被运行态覆盖，防止 MQ 乱序导致状态回退。
        return incomingOrder >= currentOrderFromDB;
    }

    private int statusOrder(String status) {
        switch (status) {
            case "NO_TASK":
                return 0;
            case "WAITING":
            case "RETRY_WAITING":
                return 1;
            case "UPGRADE_REQUESTED":
                return 2;
            case "UPGRADING":
                return 3;
            case "DISCONNECT":
                return 4;
            case "RESUME_UPGRADING":
                return 5;
            case "WAIT_RESULT":
                return 6;
            case "SUCCESS":
            case "FAIL":
            case "TIMEOUT":
            case "CANCEL_UPGRADE":
                return 7;
            default:
                return -1;
        }
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
