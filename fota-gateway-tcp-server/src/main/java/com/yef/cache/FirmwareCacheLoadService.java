package com.yef.cache;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Loads firmware objects from MinIO into local JVM cache with per-firmware concurrency control.
 */
@Slf4j
@Service
public class FirmwareCacheLoadService {

    private final MinioClient minioClient;
    private final FirmwareCacheManager firmwareCacheManager;

    /**
     * MinIO reads are blocking IO and must not run on Netty EventLoop.
     */
    private final ExecutorService firmwareLoadExecutor = new ThreadPoolExecutor(
            2,
            4,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("fota-firmware-load-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public FirmwareCacheLoadService(MinioClient minioClient,
                                    FirmwareCacheManager firmwareCacheManager) {
        this.minioClient = minioClient;
        this.firmwareCacheManager = firmwareCacheManager;
    }

    public Future<FirmwareCacheHolder> loadFirmwarePackageToLocalCache(Map<String, Object> runtimeMap) {
        if (runtimeMap == null || runtimeMap.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        Long firmwareId = Long.valueOf(String.valueOf(runtimeMap.get("firmwareId")));
        String bucketName = String.valueOf(runtimeMap.get("bucketName"));
        String objectName = String.valueOf(runtimeMap.get("objectName"));
        Long fileSize = Long.valueOf(String.valueOf(runtimeMap.get("fileSize")));
        Integer chunkSize = Integer.valueOf(String.valueOf(runtimeMap.get("chunkSize")));
        Integer totalPacket = Integer.valueOf(String.valueOf(runtimeMap.get("totalPacket")));
        String md5 = String.valueOf(runtimeMap.get("md5"));
        String imei = String.valueOf(runtimeMap.get("imei"));
        String taskId = String.valueOf(runtimeMap.get("taskId"));

        CompletableFuture<FirmwareCacheHolder> future = firmwareCacheManager.loadIfAbsent(firmwareId, () -> {
            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build())
            ) {
                long now = System.currentTimeMillis();
                byte[] fullPackageBytes = in.readAllBytes();

                FirmwareCacheHolder cacheHolder = new FirmwareCacheHolder();
                cacheHolder.setFirmwareId(firmwareId);
                cacheHolder.setFirmwareFullBytes(fullPackageBytes);
                cacheHolder.setFileSize(fileSize);
                cacheHolder.setCreatedAt(now);
                cacheHolder.setLastAccessAt(now);
                cacheHolder.setBucketName(bucketName);
                cacheHolder.setObjectName(objectName);
                cacheHolder.setChunkSize(chunkSize);
                cacheHolder.setTotalPacket(totalPacket);
                cacheHolder.setMd5(md5);
                return cacheHolder;
            } catch (Exception e) {
                throw new RuntimeException("固件缓存预加载失败, firmwareId=" + firmwareId
                        + ", bucketName=" + bucketName
                        + ", objectName=" + objectName, e);
            }
        }, firmwareLoadExecutor);

        return future.thenApply(holder -> {
            if (holder != null && holder.getFirmwareFullBytes() != null) {
                //同一批升级任务中，每一个升级请求在调用loadFirmwarePackageToLocalCache方法成功获取到holder时，则对该固件的引用计数加1
                //升级完成时，则对该引用计数减1
                holder.getRefCount().incrementAndGet();
                holder.setLastAccessAt(System.currentTimeMillis());
                log.info("固件缓存加载完成/命中, firmwareId={}, imei={}, taskId={}, fileSize={}, refCount={}",
                        firmwareId, imei, taskId, holder.getFileSize(), holder.getRefCount().get());
            }
            return holder;
        });
    }
}
