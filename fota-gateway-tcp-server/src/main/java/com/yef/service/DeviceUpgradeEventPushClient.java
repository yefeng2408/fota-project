package com.yef.service;

import com.yef.req.DeviceUpgradeEventRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class DeviceUpgradeEventPushClient {

    private final RestTemplate restTemplate;

    @Value("${upgrade.event.push-url}")
    private String pushUrl;

    public DeviceUpgradeEventPushClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void push(DeviceUpgradeEventRequest eventRequest) {
        restTemplate.postForObject(pushUrl, eventRequest, Void.class);
    }
}