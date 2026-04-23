package com.yef.service;

import java.util.Collections;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
/**
 * @description: 点击“开始升级”时，web 先用 Redis + Lua 对 imei 加锁，锁值用 taskId 充当 token。
 * 如果同一台设备已经处于升级流程中，会直接拦住，提示不能重复下发。
 * 锁默认过期时间是 90s。
 * 网关在收到平台下发后，会把运行态设成 UPGRADE_REQUESTED，并记录 lockToken。
 * 网关定时任务每 30s 只对这三种状态续期：UPGRADE_REQUESTED、UPGRADING、WAIT_RESULT。
 * 设备上报 0x06 最终结果后，网关立即删锁，同时回调 web 更新最终任务结果。
 * web 侧也会再按 imei + taskId 做一次释放，双删是安全的，因为 Lua 会校验 token。
 *
 * 补了一个保险：
 *  如果 web 侧任务记录创建失败或下发网关失败，会立即释放锁，不会把设备锁死在 Redis 里。
 *
 * @author: 叶丰
 * @date: 2026/4/22 23:02
 */
@Service
public class DeviceUpgradeLockService {

    public static final String DEVICE_UPGRADE_LOCK_KEY_PREFIX = "fota:upgrade:lock:";
    public static final String DEVICE_UPGRADE_ACTIVE_SET_KEY = "fota:upgrade:lock:active";
    private static final long DEFAULT_LOCK_EXPIRE_MS = 90_000L;

    private static final String RENEW_SCRIPT = """
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
    private final DefaultRedisScript<Long> renewRedisScript;
    private final DefaultRedisScript<Long> releaseRedisScript;

    public DeviceUpgradeLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.renewRedisScript = buildScript(RENEW_SCRIPT);
        this.releaseRedisScript = buildScript(RELEASE_SCRIPT);
    }

    public boolean renewLock(String imei, String lockToken) {
        Long result = redisTemplate.execute(
                renewRedisScript,
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

    public void markActive(String imei) {
        redisTemplate.opsForSet().add(DEVICE_UPGRADE_ACTIVE_SET_KEY, imei);
    }

    public void clearActive(String imei) {
        redisTemplate.opsForSet().remove(DEVICE_UPGRADE_ACTIVE_SET_KEY, imei);
    }

    public String buildLockKey(String imei) {
        return DEVICE_UPGRADE_LOCK_KEY_PREFIX + imei;
    }

    private DefaultRedisScript<Long> buildScript(String scriptText) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(Long.class);
        return script;
    }
}
