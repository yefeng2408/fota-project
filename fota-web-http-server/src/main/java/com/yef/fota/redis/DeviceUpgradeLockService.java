package com.yef.fota.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * @description:
 * @author: 叶丰
 * @date: 2026/4/22 23:05
 */
@Service
public class DeviceUpgradeLockService {

    public static final String DEVICE_UPGRADE_SESSION_LOCK_KEY_PREFIX = "fota:upgrade:session-lock:";
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

    public DeviceUpgradeLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.acquireRedisScript = buildScript(ACQUIRE_SCRIPT);
        this.releaseRedisScript = buildScript(RELEASE_SCRIPT);
    }

/*
    public boolean acquireLock(String imei, String lockToken) {
        return acquireLock(imei, lockToken, DEFAULT_LOCK_EXPIRE_MS);
    }

    public boolean acquireLock(String imei, String lockToken, long expireMs) {
        Long result = redisTemplate.execute(
                acquireRedisScript,
                Collections.singletonList(buildLockKey(imei)),
                lockToken,
                String.valueOf(expireMs)
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
*/

    public boolean isHeldBy(String imei, String lockToken) {
        String currentValue = redisTemplate.opsForValue().get(buildLockKey(imei));
        return lockToken != null && lockToken.equals(currentValue);
    }

    public boolean isLocked(String imei) {
        Boolean exists = redisTemplate.hasKey(buildLockKey(imei));
        return Boolean.TRUE.equals(exists);
    }

    public String buildLockKey(String imei) {
        return DEVICE_UPGRADE_SESSION_LOCK_KEY_PREFIX + imei;
    }

    private DefaultRedisScript<Long> buildScript(String scriptText) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(Long.class);
        return script;
    }
}
