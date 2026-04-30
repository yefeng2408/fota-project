package com.yef.service.redis;

import java.util.Collections;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 调度预占锁。
 * gateway 成功受理升级请求后，负责把这把短生命周期的锁释放掉。
 */
@Service
public class DeviceUpgradeDispatchLockService {

    public static final String DEVICE_UPGRADE_DISPATCH_LOCK_KEY_PREFIX = "fota:upgrade:dispatch-lock:";

    private static final String RELEASE_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> releaseRedisScript;

    public DeviceUpgradeDispatchLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.releaseRedisScript = buildScript(RELEASE_SCRIPT);
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
