package com.yef.fota.controller;

import com.alibaba.fastjson.JSON;
import com.yef.fota.api.dto.DeviceUpgradeEventRequest;
import com.yef.fota.api.dto.resp.DeviceUpgradeCancelEventResult;
import com.yef.fota.api.dto.resp.DeviceUpgradeStartTimeEventResult;
import com.yef.fota.api.dto.resp.UpdateDeviceUpgradeFinalResult;
import com.yef.fota.service.UpgradeTaskService;
import com.yef.fota.websocket.WebSocketConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description: websocket 来自设备网关的事件推送
 * @author: 叶丰
 * @date: 2026/4/21 11:02
 */
@Slf4j
@RestController
@RequestMapping("/internal/device-upgrade")
@RequiredArgsConstructor
public class DeviceUpgradeEventController {

    private final WebSocketConfig webSocketConfig;
    private final UpgradeTaskService upgradeTaskService;

    /**
     * 推送升级进度 progress进度条
     * @param event
     */
    @PostMapping("/event")
    public void pushEvent(@RequestBody DeviceUpgradeEventRequest event) {
        webSocketConfig.pushDeviceUpgradeEvent(event);
    }

    /**
     * 推送升级结果
     * @param result
     */
    @PostMapping("/result")
    public void pushResult(@RequestBody UpdateDeviceUpgradeFinalResult result) {
        upgradeTaskService.updateDeviceUpgradeFinalEventResult(result);
    }

    /**
     * 推送"取消升级"的结果
     * @param result
     */
    @PostMapping("/cancel")
    public void pushResult(@RequestBody DeviceUpgradeCancelEventResult result) {
        log.info("device-upgrade-event-result:{}", JSON.toJSONString(result));
        upgradeTaskService.updateCancelFinalEventResult(result);
        webSocketConfig.pushDeviceUpgradeCancelEvent(result);
    }


    /**
     * 推送升级开始时间
     * @param result
     */
    @PostMapping("/start-time")
    public void pushUpgradeStartTime(@RequestBody DeviceUpgradeStartTimeEventResult result) {
        log.info("device-upgrade-startTime-result:{}", JSON.toJSONString(result));
        upgradeTaskService.updateUpgradeStartTime(result);
    }

}