package com.yef.service;

import com.yef.req.DeviceUpgradeEventRequest;
import com.yef.req.UpdateDeviceUpgradeFinalResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class DeviceUpgradeEventPushClient {

    private final RestTemplate restTemplate;

    @Value("${upgrade.event.push-url}")
    private String pushUrl;

    @Value("${upgrade.event.update-result-url}")
    private String updateResultUrl;

    public DeviceUpgradeEventPushClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void push(DeviceUpgradeEventRequest eventRequest) {
        restTemplate.postForObject(pushUrl, eventRequest, Void.class);
    }


    public void updateFinalUpgradeTaskRecord(UpdateDeviceUpgradeFinalResult request) {
        restTemplate.postForObject(updateResultUrl, request, Void.class);
    }


}