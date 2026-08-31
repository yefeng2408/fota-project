package com.yef.fota.consumer;

import com.yef.UpgradeEventMessage;
import com.yef.fota.api.dto.DeviceUpgradeEventRequest;
import com.yef.fota.api.dto.resp.UpgradeCancelEventResult;
import com.yef.fota.api.dto.resp.UpgradeStartTimeEventResult;
import com.yef.fota.api.dto.resp.UpdateDeviceUpgradeFinalResult;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.UpgradeTaskEntity;
import com.yef.fota.mapper.DeviceMapper;
import com.yef.fota.mapper.UpgradeTaskMapper;
import com.yef.fota.service.UpgradeTaskService;
import com.yef.fota.websocket.WebSocketConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceUpgradeStatusEventConsumerTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private UpgradeTaskMapper upgradeTaskMapper;

    @Mock
    private UpgradeTaskService upgradeTaskService;

    @Mock
    private WebSocketConfig webSocketConfig;

    @InjectMocks
    private DeviceUpgradeStatusEventConsumer consumer;

    @Test
    void startTimeEventShouldUpdateTaskStartTime() {
        LocalDateTime startTime = LocalDateTime.of(2026, 4, 29, 10, 30, 0);
        UpgradeEventMessage event = new UpgradeEventMessage();
        event.setEventType(UpgradeEventMessage.EventType.START_TIME);
        event.setTaskId(1001L);
        event.setStartTime(startTime);

        consumer.onMessage(event);

        ArgumentCaptor<UpgradeStartTimeEventResult> captor =
                ArgumentCaptor.forClass(UpgradeStartTimeEventResult.class);
        verify(upgradeTaskService).updateUpgradeStartTime(captor.capture());
        assertEquals(1001L, captor.getValue().getTaskId());
        assertEquals(startTime, captor.getValue().getStartTime());
        verifyNoInteractions(deviceMapper, upgradeTaskMapper, webSocketConfig);
    }

    @Test
    void upgradingEventShouldUpdateDeviceTaskAndPushWebsocket() {
        UpgradeEventMessage event = new UpgradeEventMessage();
        event.setEventType(UpgradeEventMessage.EventType.UPGRADING);
        event.setImei("860000000001");
        event.setTaskId(2002L);
        event.setUpgradeStatus("UPGRADING");

        DeviceEntity device = new DeviceEntity();
        device.setId(11L);
        device.setImei("860000000001");
        UpgradeTaskEntity task = new UpgradeTaskEntity();
        task.setId(22L);
        task.setTaskId(2002L);

        when(deviceMapper.selectDeviceByImei("860000000001")).thenReturn(device);
        when(upgradeTaskMapper.selectOne(any())).thenReturn(task);

        consumer.onMessage(event);

        ArgumentCaptor<DeviceEntity> deviceCaptor = ArgumentCaptor.forClass(DeviceEntity.class);
        verify(deviceMapper).updateById(deviceCaptor.capture());
        assertEquals("UPGRADING", deviceCaptor.getValue().getDeviceUpgradeStatus());
        assertNotNull(deviceCaptor.getValue().getUpdatedAt());

        ArgumentCaptor<UpgradeTaskEntity> taskCaptor = ArgumentCaptor.forClass(UpgradeTaskEntity.class);
        verify(upgradeTaskMapper).updateById(taskCaptor.capture());
        assertEquals("UPGRADING", taskCaptor.getValue().getTaskStatus());
        assertNotNull(taskCaptor.getValue().getUpdatedAt());

        ArgumentCaptor<DeviceUpgradeEventRequest> wsCaptor =
                ArgumentCaptor.forClass(DeviceUpgradeEventRequest.class);
        verify(webSocketConfig).pushDeviceUpgradeEvent(wsCaptor.capture());
        assertEquals("860000000001", wsCaptor.getValue().getImei());
        assertEquals("UPGRADING", wsCaptor.getValue().getStatus());
    }

    @Test
    void progressEventShouldPersistWaitResultAndPushWebsocket() {
        UpgradeEventMessage event = new UpgradeEventMessage();
        event.setEventType(UpgradeEventMessage.EventType.PROGRESS);
        event.setImei("860000000002");
        event.setTaskId(3003L);
        event.setUpgradeStatus("WAIT_RESULT");
        event.setProgress(100);
        event.setCurrentFirmwareVersion("v1.0.0");
        event.setTargetFirmwareVersion("v1.0.1");

        DeviceEntity device = new DeviceEntity();
        device.setId(12L);
        device.setImei("860000000002");
        UpgradeTaskEntity task = new UpgradeTaskEntity();
        task.setId(23L);
        task.setTaskId(3003L);

        when(deviceMapper.selectDeviceByImei("860000000002")).thenReturn(device);
        when(upgradeTaskMapper.selectOne(any())).thenReturn(task);

        consumer.onMessage(event);

        ArgumentCaptor<DeviceEntity> deviceCaptor = ArgumentCaptor.forClass(DeviceEntity.class);
        verify(deviceMapper).updateById(deviceCaptor.capture());
        assertEquals("WAIT_RESULT", deviceCaptor.getValue().getDeviceUpgradeStatus());

        ArgumentCaptor<UpgradeTaskEntity> taskCaptor = ArgumentCaptor.forClass(UpgradeTaskEntity.class);
        verify(upgradeTaskMapper).updateById(taskCaptor.capture());
        assertEquals("WAIT_RESULT", taskCaptor.getValue().getTaskStatus());
        assertEquals(100, taskCaptor.getValue().getProgress());

        ArgumentCaptor<DeviceUpgradeEventRequest> wsCaptor =
                ArgumentCaptor.forClass(DeviceUpgradeEventRequest.class);
        verify(webSocketConfig).pushDeviceUpgradeEvent(wsCaptor.capture());
        assertEquals("WAIT_RESULT", wsCaptor.getValue().getStatus());
        assertEquals(100, wsCaptor.getValue().getProgress());
        assertEquals("v1.0.0", wsCaptor.getValue().getCurrentFirmwareVersion());
        assertEquals("v1.0.1", wsCaptor.getValue().getTargetFirmwareVersion());
    }

    @Test
    void finalResultEventShouldDelegateToServiceAndPushWebsocket() {
        LocalDateTime startTime = LocalDateTime.of(2026, 4, 29, 10, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 4, 29, 10, 5, 0);
        UpgradeEventMessage event = new UpgradeEventMessage();
        event.setEventType(UpgradeEventMessage.EventType.FINAL_RESULT);
        event.setImei("860000000003");
        event.setTaskId(4004L);
        event.setUpgradeStatus("SUCCESS");
        event.setProgress(100);
        event.setCurrentFirmwareVersion("v1.0.1");
        event.setTargetFirmwareVersion("v1.0.1");
        event.setCurrentPacket(10);
        event.setTotalPacket(10);
        event.setStartTime(startTime);
        event.setEndTime(endTime);

        consumer.onMessage(event);

        ArgumentCaptor<UpdateDeviceUpgradeFinalResult> serviceCaptor =
                ArgumentCaptor.forClass(UpdateDeviceUpgradeFinalResult.class);
        verify(upgradeTaskService).updateDeviceUpgradeFinalEventResult(serviceCaptor.capture());
        assertEquals("860000000003", serviceCaptor.getValue().getImei());
        assertEquals("4004", serviceCaptor.getValue().getTaskId());
        assertEquals("SUCCESS", serviceCaptor.getValue().getTaskStatus());
        assertEquals(100, serviceCaptor.getValue().getProgress());
        assertEquals(10, serviceCaptor.getValue().getCurrentPacket());
        assertEquals(10, serviceCaptor.getValue().getTotalPacket());
        assertEquals(endTime, serviceCaptor.getValue().getEndTime());

        ArgumentCaptor<DeviceUpgradeEventRequest> wsCaptor =
                ArgumentCaptor.forClass(DeviceUpgradeEventRequest.class);
        verify(webSocketConfig).pushDeviceUpgradeEvent(wsCaptor.capture());
        assertEquals("SUCCESS", wsCaptor.getValue().getStatus());
        assertEquals(100, wsCaptor.getValue().getProgress());

        verifyNoInteractions(deviceMapper);
        verify(upgradeTaskMapper, never()).updateById(any());
    }

    @Test
    void cancelResultEventShouldDelegateToServiceAndPushCancelWebsocket() {
        UpgradeEventMessage event = new UpgradeEventMessage();
        event.setEventType(UpgradeEventMessage.EventType.CANCEL_RESULT);
        event.setImei("860000000004");
        event.setUpgradeStatus("CANCEL_UPGRADE");

        consumer.onMessage(event);

        ArgumentCaptor<UpgradeCancelEventResult> serviceCaptor =
                ArgumentCaptor.forClass(UpgradeCancelEventResult.class);
        verify(upgradeTaskService).updateCancelFinalEventResult(serviceCaptor.capture());
        assertEquals("860000000004", serviceCaptor.getValue().getImei());
        assertEquals("CANCEL_UPGRADE", serviceCaptor.getValue().getStatus());

        ArgumentCaptor<UpgradeCancelEventResult> wsCaptor =
                ArgumentCaptor.forClass(UpgradeCancelEventResult.class);
        verify(webSocketConfig).pushDeviceUpgradeCancelEvent(wsCaptor.capture());
        assertEquals("860000000004", wsCaptor.getValue().getImei());
        assertEquals("CANCEL_UPGRADE", wsCaptor.getValue().getStatus());

        verifyNoInteractions(deviceMapper, upgradeTaskMapper);
    }
}
