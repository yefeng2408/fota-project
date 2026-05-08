package com.yef.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * @description: 调度器释放引用计数为0固件缓存对象
 * @author: 叶丰
 * @date: 2026/5/7 10:47
 */
@Slf4j
@Component
public class FirmwareCacheScheduler {

    // 正常释放阈值：refCount 已经归零后，闲置 5 分钟再释放，避免刚释放引用就立刻被下一次分包读取打断。
    private static final long IDLE_RELEASE_MS = 1000L * 60 * 5;

    // 强制释放阈值：refCount 长时间不归零，说明可能存在设备掉线、超时链路未释放、异常链路未 release 等问题。
    private static final long FORCE_RELEASE_MS = 1000L * 60 * 30;

    @Autowired
    private FirmwareCacheManager firmwareCacheManager;

    //每分钟调度一次
    @Scheduled(fixedDelay = 1000 * 60)
    public void clearIdleFirmwareCache() {
        Map<Long, FirmwareCacheHolder> firmwareCacheHolderMap = firmwareCacheManager.firmwareCache();
        long now = System.currentTimeMillis();

        for (Map.Entry<Long, FirmwareCacheHolder> entry : firmwareCacheHolderMap.entrySet()) {
            FirmwareCacheHolder firmwareCacheHolder = entry.getValue();
            if (firmwareCacheHolder == null || firmwareCacheHolder.getFirmwareId() == null) {
                continue;
            }

            Long firmwareId = firmwareCacheHolder.getFirmwareId();
            long lastAccessAt = firmwareCacheHolder.getLastAccessAt();
            long idleMs = now - lastAccessAt;
            int refCount = firmwareCacheHolder.getRefCount().get();

            // 正常释放：没有任何设备继续引用该固件，并且已经闲置超过 5 分钟。
            if (refCount <= 0 && idleMs > IDLE_RELEASE_MS) {
                firmwareCacheManager.remove(firmwareId);
                log.info(
                        "固件缓存正常释放 firmwareId={}, refCount={}, idleMs={}, cacheSize={}",
                        firmwareId,
                        refCount,
                        idleMs,
                        firmwareCacheManager.size()
                );
                continue;
            }

            // 强制兜底释放：refCount 长时间不归零，但固件已经超过 30 分钟无人访问。
            // 这种情况一般说明某些异常链路没有 release，比如设备掉线、升级超时、Future 异常、取消升级等。
            if (idleMs > FORCE_RELEASE_MS) {
                firmwareCacheManager.remove(firmwareId);
                log.warn(
                        "固件缓存强制释放 firmwareId={}, refCount={}, idleMs={}, cacheSize={}",
                        firmwareId,
                        refCount,
                        idleMs,
                        firmwareCacheManager.size()
                );
            }
        }
    }

}