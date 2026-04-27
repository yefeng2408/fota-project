package com.yef.service;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class DeviceUpgradeLockRenewScheduler {

    private static final String UPGRADE_RUNTIME_KEY_PREFIX = "fota:upgrade:runtime:";

    private final StringRedisTemplate redisTemplate;
    private final DeviceUpgradeLockService deviceUpgradeLockService;

    public DeviceUpgradeLockRenewScheduler(StringRedisTemplate redisTemplate,
                                           DeviceUpgradeLockService deviceUpgradeLockService) {
        this.redisTemplate = redisTemplate;
        this.deviceUpgradeLockService = deviceUpgradeLockService;
    }

    @Scheduled(fixedDelayString = "${upgrade.lock.renew-interval-ms:30000}")
    public void renewActiveLocks() {
        Set<String> imeiSet = redisTemplate.opsForSet().members(DeviceUpgradeLockService.DEVICE_UPGRADE_ACTIVE_SET_KEY);
        if (imeiSet == null || imeiSet.isEmpty()) {
            return;
        }
        for (String imei : imeiSet) {
            Map<Object, Object> runtimeMap = redisTemplate.opsForHash().entries(UPGRADE_RUNTIME_KEY_PREFIX + imei);
            if (runtimeMap == null || runtimeMap.isEmpty()) {
                deviceUpgradeLockService.clearActive(imei);
                continue;
            }

            String status = String.valueOf(runtimeMap.get("status"));
            String lockToken = String.valueOf(runtimeMap.get("lockToken"));
            if (!StringUtils.hasText(lockToken) || "null".equalsIgnoreCase(lockToken)) {
                deviceUpgradeLockService.clearActive(imei);
                continue;
            }
            if (isActiveUpgradeStatus(status)) {
                boolean renewed = deviceUpgradeLockService.renewLock(imei, lockToken);
                if (!renewed) {
                    log.warn("升级锁续期失败，imei={}, status={}", imei, status);
                }
            }else {
                log.info("状态非 active，但不主动释放锁，由业务线程控制释放");
            }
            /*deviceUpgradeLockService.releaseLock(imei, lockToken);
            deviceUpgradeLockService.clearActive(imei);*/
        }
    }

    private boolean isActiveUpgradeStatus(String status) {
        return Objects.equals("UPGRADE_REQUESTED", status)
                || Objects.equals("UPGRADING", status)
                || Objects.equals("WAIT_RESULT", status);
    }
}
