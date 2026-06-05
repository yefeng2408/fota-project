package com.yef.fota.api.service;

import com.yef.fota.api.GatewayRoute;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Resolves the gateway instance that currently owns a device TCP connection.
 */
@Slf4j
@Service
public class GatewayRouteService {

    private static final String DEVICE_ONLINE_KEY_PREFIX = "fota:device:online:";
    private static final String GATEWAY_INSTANCE_KEY_PREFIX = "fota:gateway:instance:";

    private final StringRedisTemplate redisTemplate;
    private final String fallbackGatewayHttpUrl;
    private final boolean fallbackEnabled;

    public GatewayRouteService(StringRedisTemplate redisTemplate,
                               @Value("${gateway.http.url:}") String fallbackGatewayHttpUrl,
                               @Value("${gateway.route.fallback-enabled:true}") boolean fallbackEnabled) {
        this.redisTemplate = redisTemplate;
        this.fallbackGatewayHttpUrl = trimTrailingSlash(fallbackGatewayHttpUrl);
        this.fallbackEnabled = fallbackEnabled;
    }

    public GatewayRoute findRouteByImei(String imei) {
        if (!StringUtils.hasText(imei)) {
            return fallbackRoute(imei, "imei is blank");
        }

        String onlineKey = DEVICE_ONLINE_KEY_PREFIX + imei;
        Map<Object, Object> online = redisTemplate.opsForHash().entries(onlineKey);
        if (online == null || online.isEmpty()) {
            return fallbackRoute(imei, "device online route missing");
        }

        String instanceId = value(online.get("instanceId"));
        if (!StringUtils.hasText(instanceId)) {
            return fallbackRoute(imei, "device online route has no instanceId");
        }

        String instanceKey = GATEWAY_INSTANCE_KEY_PREFIX + instanceId;
        Map<Object, Object> instance = redisTemplate.opsForHash().entries(instanceKey);
        if (instance == null || instance.isEmpty()) {
            return fallbackRoute(imei, "gateway instance heartbeat expired, instanceId=" + instanceId);
        }

        String httpUrl = trimTrailingSlash(value(instance.get("httpUrl")));
        if (!StringUtils.hasText(httpUrl)) {
            return fallbackRoute(imei, "gateway instance has no httpUrl, instanceId=" + instanceId);
        }

        GatewayRoute route = new GatewayRoute();
        route.setImei(imei);
        route.setInstanceId(instanceId);
        route.setSessionId(value(online.get("sessionId")));
        route.setChannelId(value(online.get("channelId")));
        route.setHttpUrl(httpUrl);
        route.setLastSeenAt(longValue(online.get("lastSeenAt")));
        return route;
    }

    private GatewayRoute fallbackRoute(String imei, String reason) {
        if (!fallbackEnabled || !StringUtils.hasText(fallbackGatewayHttpUrl)) {
            log.warn("gateway route resolve failed, imei={}, reason={}", imei, reason);
            return null;
        }
        log.warn("gateway route resolve failed, use fallback gateway url, imei={}, reason={}, fallback={}",
                imei, reason, fallbackGatewayHttpUrl);
        GatewayRoute route = new GatewayRoute();
        route.setImei(imei);
        route.setHttpUrl(fallbackGatewayHttpUrl);
        return route;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
