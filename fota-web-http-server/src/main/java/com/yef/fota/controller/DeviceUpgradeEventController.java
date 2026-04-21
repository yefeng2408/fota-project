package com.yef.fota.controller;

import com.alibaba.fastjson.JSON;
import com.yef.fota.api.dto.DeviceUpgradeEvent;
import com.yef.fota.api.dto.UpdateDeviceUpgradeFinalResult;
import com.yef.fota.service.UpgradeTaskService;
import com.yef.fota.websocket.WebSocketConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description: websocket 事件推送
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

    @PostMapping("/event")
    public void pushEvent(@RequestBody DeviceUpgradeEvent event) {
        log.info("======>网关升级进度推送结果："+ JSON.toJSONString(event));
        webSocketConfig.pushDeviceUpgradeEvent(event);
    }


    @PostMapping("/result")
    public void pushResult(@RequestBody UpdateDeviceUpgradeFinalResult result) {
        log.info("======>推送升级最终结果："+ JSON.toJSONString(result));
        upgradeTaskService.updateDeviceUpgradeFinalResult(result);
    }


}