package com.yef.fota.api;

import lombok.Data;

@Data
public class GatewayRoute {

    private String imei;
    private String instanceId;
    private String sessionId;
    private String channelId;
    private String httpUrl;
    private Long lastSeenAt;
}
