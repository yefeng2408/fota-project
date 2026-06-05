package com.yef.registry;

import com.yef.session.DeviceSession;
import io.netty.channel.Channel;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Maintains imei -> gateway instance/session routing for multi-gateway command dispatch.
 */
@Service
public class DeviceOnlineRegistry {

    public static final String DEVICE_ONLINE_KEY_PREFIX = "fota:device:online:";

    private static final String DELETE_IF_SESSION_MATCHES_SCRIPT = """
            if redis.call('hget', KEYS[1], 'sessionId') == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """;

    private final StringRedisTemplate redisTemplate;
    private final GatewayRegistryService gatewayRegistryService;
    private final long ttlSeconds;
    private final DefaultRedisScript<Long> deleteIfSessionMatchesScript;

    public DeviceOnlineRegistry(StringRedisTemplate redisTemplate,
                                GatewayRegistryService gatewayRegistryService,
                                @Value("${gateway.device-online.ttl-seconds:240}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.gatewayRegistryService = gatewayRegistryService;
        this.ttlSeconds = ttlSeconds;
        this.deleteIfSessionMatchesScript = new DefaultRedisScript<>();
        this.deleteIfSessionMatchesScript.setScriptText(DELETE_IF_SESSION_MATCHES_SCRIPT);
        this.deleteIfSessionMatchesScript.setResultType(Long.class);
    }

    public void register(DeviceSession session,
                         String currentFirmwareVersion,
                         String deviceType,
                         String remoteAddress) {
        if (session == null || !StringUtils.hasText(session.getImei()) || !StringUtils.hasText(session.getSessionId())) {
            return;
        }

        long now = System.currentTimeMillis();
        Map<String, String> fields = new HashMap<>();
        fields.put("imei", session.getImei());
        fields.put("instanceId", gatewayRegistryService.getInstanceId());
        fields.put("sessionId", session.getSessionId());
        fields.put("channelId", channelId(session.getChannel()));
        fields.put("connectedAt", String.valueOf(session.getConnectTime()));
        fields.put("lastSeenAt", String.valueOf(now));
        fields.put("deviceType", nullToEmpty(deviceType));
        fields.put("currentFirmwareVersion", nullToEmpty(currentFirmwareVersion));
        fields.put("remoteAddress", nullToEmpty(remoteAddress));

        String key = buildOnlineKey(session.getImei());
        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
    }

    public void touch(DeviceSession session) {
        if (session == null || !StringUtils.hasText(session.getImei()) || !StringUtils.hasText(session.getSessionId())) {
            return;
        }
        String key = buildOnlineKey(session.getImei());
        redisTemplate.opsForHash().put(key, "lastSeenAt", String.valueOf(System.currentTimeMillis()));
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
    }

    public boolean removeIfSessionMatches(String imei, String sessionId) {
        if (!StringUtils.hasText(imei) || !StringUtils.hasText(sessionId)) {
            return false;
        }
        Long result = redisTemplate.execute(
                deleteIfSessionMatchesScript,
                Collections.singletonList(buildOnlineKey(imei)),
                sessionId
        );
        return result != null && result == 1L;
    }

    public String buildOnlineKey(String imei) {
        return DEVICE_ONLINE_KEY_PREFIX + imei;
    }

    private String channelId(Channel channel) {
        return channel == null ? "" : channel.id().asLongText();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
