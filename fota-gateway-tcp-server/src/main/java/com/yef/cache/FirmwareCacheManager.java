package com.yef.cache;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * @description: 固件本地缓存管理器。小固件先使用网关 JVM 内存缓存，后续固件变大时可升级为本地文件 / mmap。
 * @author: 叶丰
 * @date: 2026/5/6 15:41
 */
@Component
public class FirmwareCacheManager {

    private final Map<Long, FirmwareCacheHolder> firmwareCache = new ConcurrentHashMap<>();

    /**
     * 正在加载中的固件 Future。
     *
     * key = firmwareId
     * value = 当前 firmwareId 对应的异步加载任务
     */
    private final Map<Long, CompletableFuture<FirmwareCacheHolder>> loadingFutureMap = new ConcurrentHashMap<>();

    public Map<Long, FirmwareCacheHolder> firmwareCache() {
        return firmwareCache;
    }

    public FirmwareCacheHolder get(Long firmwareId) {
        return firmwareCache.get(firmwareId);
    }

    public FirmwareCacheHolder putIfAbsent(Long firmwareId, FirmwareCacheHolder holder) {
        return firmwareCache.putIfAbsent(firmwareId, holder);
    }

    /**
     * 固件加载入口。
     *
     * 同一个 firmwareId 在高并发场景下：
     * 1. 只允许一个线程真正去 MinIO 下载。
     * 2. 其它线程直接复用同一个 Future 等待结果。
     * 3. 加载完成后写入 firmwareCache，并移除 loadingFutureMap。
     */
    public CompletableFuture<FirmwareCacheHolder> loadIfAbsent(Long firmwareId,
                                                                Supplier<FirmwareCacheHolder> loader,
                                                                ExecutorService executorService) {

        FirmwareCacheHolder existingHolder = firmwareCache.get(firmwareId);
        if (existingHolder != null && existingHolder.getFirmwareFullBytes() != null) {
            return CompletableFuture.completedFuture(existingHolder);
        }

        return loadingFutureMap.computeIfAbsent(firmwareId, key -> {
            CompletableFuture<FirmwareCacheHolder> future = CompletableFuture.supplyAsync(() -> {
                FirmwareCacheHolder holder = loader.get();
                firmwareCache.put(firmwareId, holder);
                return holder;
            }, executorService);

            future.whenComplete((holder, ex) -> {
                loadingFutureMap.remove(firmwareId);
            });

            return future;
        });
    }

    public boolean contains(Long firmwareId) {
        return firmwareCache.containsKey(firmwareId);
    }


    public CompletableFuture<FirmwareCacheHolder> getLoadingFuture(Long firmwareId) {
        return loadingFutureMap.get(firmwareId);
    }

    public int size() {
        return firmwareCache.size();
    }
    public void remove(Long firmwareId) {
        firmwareCache.remove(firmwareId);
    }


}