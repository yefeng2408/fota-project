package com.yef.fota.redis;

import java.util.Collections;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 调度预占锁。
 * web 在真正调用 gateway 之前先占住设备，防止同一台设备被重复调度。
 */
@Service
public class DeviceUpgradeDispatchLockService {

    public static final String DEVICE_UPGRADE_DISPATCH_LOCK_KEY_PREFIX = "fota:upgrade:dispatch-lock:";
    private static final long DEFAULT_LOCK_EXPIRE_MS = 90_000L;

    private static final String ACQUIRE_SCRIPT = """
            if redis.call('exists', KEYS[1]) == 0 then
                redis.call('psetex', KEYS[1], ARGV[2], ARGV[1])
                return 1
            end
            if redis.call('get', KEYS[1]) == ARGV[1] then
                redis.call('pexpire', KEYS[1], ARGV[2])
                return 1
            end
            return 0
            """;

    private static final String RELEASE_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> acquireRedisScript;
    private final DefaultRedisScript<Long> releaseRedisScript;

    public DeviceUpgradeDispatchLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.acquireRedisScript = buildScript(ACQUIRE_SCRIPT);
        this.releaseRedisScript = buildScript(RELEASE_SCRIPT);
    }

    public boolean acquireLock(String imei, String lockToken) {
        Long result = redisTemplate.execute(
                acquireRedisScript,
                Collections.singletonList(buildLockKey(imei)),
                lockToken,
                String.valueOf(DEFAULT_LOCK_EXPIRE_MS)
        );
        return result != null && result == 1L;
    }

    public boolean releaseLock(String imei, String lockToken) {
        Long result = redisTemplate.execute(
                releaseRedisScript,
                Collections.singletonList(buildLockKey(imei)),
                lockToken
        );
        return result != null && result == 1L;
    }

    public String buildLockKey(String imei) {
        return DEVICE_UPGRADE_DISPATCH_LOCK_KEY_PREFIX + imei;
    }

    private DefaultRedisScript<Long> buildScript(String scriptText) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(Long.class);
        return script;
    }
}
