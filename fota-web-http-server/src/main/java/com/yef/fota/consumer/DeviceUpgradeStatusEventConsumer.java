package com.yef.fota.consumer;

import com.yef.fota.dto.consumer.DeviceUpgradeStatusEvent;
import com.yef.fota.mapper.DeviceMapper;
import com.yef.fota.mapper.UpgradeTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @description: TODO
 * @author: yefeng
 * @date: 2026/04/15 01:24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceUpgradeStatusEventConsumer {

    private final DeviceMapper deviceMapper;
    private final UpgradeTaskMapper upgradeTaskMapper;

    public void consume(DeviceUpgradeStatusEvent event) {
        int deviceRows = deviceMapper.updateUpgradeStatusIfNewer(
                event.getDeviceId(),
                event.getStatus(),
                event.getVersion(),
                event.getEventTime()
        );

        if (deviceRows == 0) {
            log.warn("忽略旧设备状态事件, deviceId={}, status={}, version={}",
                    event.getDeviceId(), event.getStatus(), event.getVersion());
        }

        int taskRows = upgradeTaskMapper.updateTaskStatusIfNewer(
                event.getTaskId(),
                event.getStatus(),
                event.getVersion(),
                event.getEventTime()
        );

        if (taskRows == 0) {
            log.warn("忽略旧任务状态事件, taskId={}, status={}, version={}",
                    event.getTaskId(), event.getStatus(), event.getVersion());
        }
    }
}