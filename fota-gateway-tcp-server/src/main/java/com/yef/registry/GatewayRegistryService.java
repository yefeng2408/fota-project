package com.yef.registry;

import com.yef.session.SessionManager;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Registers the current gateway instance so web can route device commands to it.
 */
@Slf4j
@Service
public class GatewayRegistryService {

    public static final String GATEWAY_INSTANCES_KEY = "fota:gateway:instances";
    public static final String GATEWAY_INSTANCE_KEY_PREFIX = "fota:gateway:instance:";

    private final StringRedisTemplate redisTemplate;
    private final SessionManager sessionManager;
    //注入的value为空字符串
    private final String configuredInstanceId;
    //注入的value为空字符串
    private final String configuredHttpUrl;
    //注入的value为空字符串
    private final String configuredTcpHost;
    private final int serverPort;
    private final int tcpPort;
    private final int weight;
    //加 TTL 是为了实现 gateway 实例心跳和故障自动摘除。
    // 否则 gateway 异常下线后，Redis 会残留假在线实例，web 可能把升级指令下发到已经挂掉的 gateway
    private final long ttlSeconds;

    private String instanceId;
    private String httpUrl;
    private String tcpHost;
    private long startedAt;

    public GatewayRegistryService(StringRedisTemplate redisTemplate,
                                  SessionManager sessionManager,
                                  @Value("${gateway.instance.id:}") String configuredInstanceId,
                                  @Value("${gateway.instance.http-url:}") String configuredHttpUrl,
                                  @Value("${gateway.instance.tcp-host:}") String configuredTcpHost,
                                  @Value("${server.port:8081}") int serverPort,
                                  @Value("${netty.server.port:7611}") int tcpPort,
                                  @Value("${gateway.instance.weight:100}") int weight,
                                  @Value("${gateway.instance.ttl-seconds:30}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.sessionManager = sessionManager;
        this.configuredInstanceId = configuredInstanceId;
        this.configuredHttpUrl = configuredHttpUrl;
        this.configuredTcpHost = configuredTcpHost;
        this.serverPort = serverPort;
        this.tcpPort = tcpPort;
        this.weight = weight;
        this.ttlSeconds = ttlSeconds;
    }

    @PostConstruct
    public void init() {
        String host = resolveHostName();
        this.startedAt = System.currentTimeMillis();
        this.instanceId = StringUtils.hasText(configuredInstanceId)
                ? configuredInstanceId
                : "gateway-" + host + "-" + tcpPort + "-" + startedAt;
        this.tcpHost = StringUtils.hasText(configuredTcpHost) ? configuredTcpHost : host;
        this.httpUrl = StringUtils.hasText(configuredHttpUrl) ? configuredHttpUrl : "http://" + host + ":" + serverPort;
        registerHeartbeat();
        log.info("gateway instance registered, instanceId={}, httpUrl={}, tcpHost={}, tcpPort={}",
                instanceId, httpUrl, tcpHost, tcpPort);
    }

    @Scheduled(fixedDelayString = "${gateway.instance.heartbeat-interval-ms:10000}")
    public void registerHeartbeat() {
        if (!StringUtils.hasText(instanceId)) {
            return;
        }

        long now = System.currentTimeMillis();
        Map<String, String> fields = new HashMap<>();
        fields.put("instanceId", instanceId);
        fields.put("httpUrl", httpUrl);
        fields.put("tcpHost", tcpHost);
        fields.put("tcpPort", String.valueOf(tcpPort));
        fields.put("status", "UP");
        fields.put("weight", String.valueOf(weight));
        fields.put("activeConnections", String.valueOf(sessionManager.onlineSessionCount()));
        fields.put("startedAt", String.valueOf(startedAt));
        fields.put("lastHeartbeatAt", String.valueOf(now));

        String instanceKey = buildInstanceKey(instanceId);
        redisTemplate.opsForHash().putAll(instanceKey, fields);
        redisTemplate.expire(instanceKey, java.time.Duration.ofSeconds(ttlSeconds));
        redisTemplate.opsForSet().add(GATEWAY_INSTANCES_KEY, instanceId);
    }

    @PreDestroy
    public void destroy() {
        if (!StringUtils.hasText(instanceId)) {
            return;
        }
        redisTemplate.delete(buildInstanceKey(instanceId));
        redisTemplate.opsForSet().remove(GATEWAY_INSTANCES_KEY, instanceId);
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getHttpUrl() {
        return httpUrl;
    }

    public String buildInstanceKey(String gatewayInstanceId) {
        return GATEWAY_INSTANCE_KEY_PREFIX + gatewayInstanceId;
    }

    private String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-" + Instant.now().toEpochMilli();
        }
    }
}
