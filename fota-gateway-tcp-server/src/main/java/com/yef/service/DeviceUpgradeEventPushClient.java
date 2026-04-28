package com.yef.service;

import com.yef.req.DeviceUpgradeCancelEventRequest;
import com.yef.req.DeviceUpgradeEventRequest;
import com.yef.req.DeviceUpgradeStartTimeRequest;
import com.yef.req.UpdateDeviceUpgradeFinalResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
/**
 * @description: 基于升级状态流转 状态变化的事件消息推送
 * @author: 叶丰
 * @date: 2026/4/28 20:34
 */
@Service
public class DeviceUpgradeEventPushClient {

    private final RestTemplate restTemplate;

    @Value("${upgrade.event.update-progress-url}")
    private String updateProgressUrl;

    @Value("${upgrade.event.update-result-url}")
    private String updateResultUrl;

    @Value("${upgrade.event.update-cancel-url}")
    private String updateCancelUrl;

    @Value("${upgrade.event.update-start-url}")
    private String updateStartTimeUrl;

    public DeviceUpgradeEventPushClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 推送升级进度progress【进度条】
     * @param eventRequest
     */
    public void pushUpgradeProgress(DeviceUpgradeEventRequest eventRequest) {
        restTemplate.postForObject(updateProgressUrl, eventRequest, Void.class);
    }


    /**
     * 推送最终升级结果。本想将升级任务开始时间也放在这个接口里面去调用web服务接口做更新，
     *                但是万一升级过程中出问题了，那么网关就会无法调用此接口，就会导致web服务设备丢失升级开始时间
     * @param request
     */
    public void updateFinalUpgradeTaskRecord(UpdateDeviceUpgradeFinalResult request) {
        restTemplate.postForObject(updateResultUrl, request, Void.class);
    }


    /**
     * 推送取消升级结果
     * @param request
     */
    public void updateCancelResult(DeviceUpgradeCancelEventRequest request) {
        restTemplate.postForObject(updateCancelUrl, request, Void.class);
    }

    /**
     * 推送升级开始时间。升级开始时间的定义：由网关下发设备的第一个分包数据开始为准
     * @param request
     */
    public void updateStartTime(DeviceUpgradeStartTimeRequest request) {
        restTemplate.postForObject(updateStartTimeUrl, request, Void.class);
    }
}