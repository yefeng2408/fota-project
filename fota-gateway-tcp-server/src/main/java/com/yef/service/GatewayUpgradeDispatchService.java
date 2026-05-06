package com.yef.service;

import com.yef.cache.FirmwareCacheHolder;
import com.yef.cache.FirmwareCacheManager;
import com.yef.dto.PlatformCancelUpgradeRequest;
import com.yef.dto.PlatformUpgradeRequest;
import com.yef.exception.FotaProtocolException;
import com.yef.protocol.CancelUpgradeMessage;
import com.yef.protocol.UpgradeRequestMessage;
import com.yef.service.redis.DeviceUpgradeDispatchLockService;
import com.yef.session.DeviceSession;
import com.yef.session.SessionManager;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class GatewayUpgradeDispatchService {

    /**
     * 升级运行态 设备基础信息 设备网关所用的key，用于分包过程中的【高频写操作】
     */
    private static final String UPGRADE_RUNTIME_KEY_PREFIX = "fota:upgrade:runtime:";

    private final MinioClient minioClient;
    private final SessionManager sessionManager;
    private final StringRedisTemplate redisTemplate;
    private final DeviceUpgradeLockService deviceUpgradeLockService;
    private final DeviceUpgradeDispatchLockService deviceUpgradeDispatchLockService;
    private final FirmwareCacheManager firmwareCacheManager;

    /**
     * 固件预加载线程池：MinIO 读取属于阻塞 IO，不能占用 Netty EventLoop。
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

    public GatewayUpgradeDispatchService(MinioClient minioClient,
                                         SessionManager sessionManager,
                                         StringRedisTemplate redisTemplate,
                                         DeviceUpgradeLockService deviceUpgradeLockService,
                                         DeviceUpgradeDispatchLockService deviceUpgradeDispatchLockService,
                                         FirmwareCacheManager firmwareCacheManager) {
        this.minioClient = minioClient;
        this.sessionManager = sessionManager;
        this.redisTemplate = redisTemplate;
        this.deviceUpgradeLockService = deviceUpgradeLockService;
        this.deviceUpgradeDispatchLockService = deviceUpgradeDispatchLockService;
        this.firmwareCacheManager = firmwareCacheManager;
    }

    public void sendUpgradeRequest(PlatformUpgradeRequest req) {
        DeviceSession session = sessionManager.getByImei(req.getImei());
        if (session == null || session.getChannel() == null || !session.getChannel().isActive()) {
            throw new FotaProtocolException("设备不在线，无法下发升级请求");
        }
        if (!deviceUpgradeLockService.acquireLock(req.getImei(), req.getLockToken())) {
            throw new FotaProtocolException("设备升级会话已存在，拒绝重复受理");
        }

        try {
            UpgradeRequestMessage message = new UpgradeRequestMessage(
                    req.getImei(),
                    req.getTaskId(),
                    req.getFirmwareId(),
                    req.getFirmwareNameLen(),
                    req.getFirmwareName(),
                    req.getFirmwareVersionLen(),
                    req.getFirmwareVersionName(),
                    req.getTotalPacket(),
                    req.getChunkSize(),
                    req.getFileSize(),
                    hexMd5ToBytes(req.getMd5())
            );
            // 网关收到平台下发 0x81 消息。先初始化 runtime，再异步预加载固件缓存，最后下发 0x81。
            Map<String, Object> runtimeHash = getRuntimeHash(req);
            redisTemplate.opsForHash().putAll(UPGRADE_RUNTIME_KEY_PREFIX + req.getImei(), runtimeHash);
            Future<FirmwareCacheHolder> future = loadFirmwarePackageToLocalCache(runtimeHash);
            FirmwareCacheHolder firmwareCacheHolder = future.get(10, TimeUnit.SECONDS);
            if (firmwareCacheHolder == null || !req.getFirmwareId().equals(firmwareCacheHolder.getFirmwareId())) {
                throw new FotaProtocolException("固件缓存加载失败，拒绝下发升级请求");
            }

            Channel channel = session.getChannel();
            channel.writeAndFlush(message);
            deviceUpgradeLockService.markActive(req.getImei());
            deviceUpgradeDispatchLockService.releaseLock(req.getImei(), req.getLockToken());
        } catch (RuntimeException ex) {
            deviceUpgradeLockService.releaseLock(req.getImei(), req.getLockToken());
            throw ex;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deviceUpgradeLockService.releaseLock(req.getImei(), req.getLockToken());
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            deviceUpgradeLockService.releaseLock(req.getImei(), req.getLockToken());
            throw new FotaProtocolException("固件缓存加载超时，拒绝下发升级请求");
        }

    }

    @NotNull
    private Map<String, Object> getRuntimeHash(PlatformUpgradeRequest req) {

        Map<String, Object> runtimeHash = new HashMap<>();
        runtimeHash.put("imei", nullToEmpty(req.getImei()));
        runtimeHash.put("taskId", nullToEmpty(String.valueOf(req.getTaskId())));
        runtimeHash.put("lockToken", nullToEmpty(req.getLockToken()));
        runtimeHash.put("status", "UPGRADE_REQUESTED");

        runtimeHash.put("startAt",String.valueOf(0));
        runtimeHash.put("endAt",String.valueOf(0));
        runtimeHash.put("progress",String.valueOf(0));


        runtimeHash.put("firmwareId", String.valueOf(req.getFirmwareId()));
        runtimeHash.put("bucketName", req.getBucketName());
        runtimeHash.put("objectName", req.getObjectName());
        runtimeHash.put("packetNo", String.valueOf(0));
        runtimeHash.put("totalPacket", nullToEmpty(String.valueOf(req.getTotalPacket())));
        runtimeHash.put("chunkSize", nullToEmpty(String.valueOf(req.getChunkSize())));
        runtimeHash.put("fileSize", String.valueOf(nullToEmpty(req.getFileSize())));
        runtimeHash.put("md5", nullToEmpty(req.getMd5()));

        runtimeHash.put("targetFirmwareVersion", req.getFirmwareVersionName());
        return runtimeHash;
    }


    /**
     * 网关收到平台 0x81 升级请求后，基于 runtimeKey 中的固件信息加载固件到本地 JVM 内存。
     *
     * 重点：
     * 1. 这里不是在方法上加 synchronized，也不是手写分布式锁。
     * 2. 真正的并发控制放在 FirmwareCacheManager.loadIfAbsent 里。
     * 3. 同一个 firmwareId 批量升级 800 台设备时，只会有一个线程真正访问 MinIO，其他线程等待同一个 Future。
     */
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
                holder.getRefCount().incrementAndGet();
                holder.setLastAccessAt(System.currentTimeMillis());
                log.info("固件缓存加载完成/命中, firmwareId={}, imei={}, taskId={}, fileSize={}, refCount={}",
                        firmwareId, imei, taskId, holder.getFileSize(), holder.getRefCount().get());
            }
            return holder;
        });
    }


    /**
     * 判断指定桶中的对象是否存在
     *
     * @param bucketName 桶名称
     * @param objectName 对象路径/文件名
     * @return true-存在, false-不存在
     */
    public boolean doesObjectExist(String bucketName, String objectName) {
        try {
            // 尝试获取对象元数据
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
            // 如果没有抛出异常，说明对象存在
            return true;

        } catch (ErrorResponseException e) {
            // 如果是服务器返回404 "NoSuchKey" 错误，说明对象不存在
            // 同时打印日志，方便排查
            log.debug("Object [{}] not found in bucket [{}]: {}", objectName, bucketName, e.errorResponse().code());
            return false;

        } catch (Exception e) {
            // 其他错误（如网络、权限问题）归于异常，可记录日志后向上抛出
            throw new RuntimeException("检查文件存在性时发生未知错误", e);
        }
    }




    public void sendCancelUpgradeRequest(PlatformCancelUpgradeRequest request) {
        log.info("------>取消升级接受http入参sendCancelUpgradeRequest，taskId:{}",request.getTaskId());
        DeviceSession session = sessionManager.getByImei(request.getImei());
        if (session == null || session.getChannel() == null || !session.getChannel().isActive()) {
            throw new FotaProtocolException("设备不在线，无法下发取消升级指令");
        }
        //组装 CancelUpgradeMessage(0x87)，然后触发写出站事件 writeAndFlush
        CancelUpgradeMessage message = new CancelUpgradeMessage(request.getImei(),request.getTaskId(),request.getReason());

        Channel channel = session.getChannel();
        channel.writeAndFlush(message);

    }



    private byte[] hexMd5ToBytes(String md5) {
        if (md5 == null || md5.length() != 32) {
            throw new FotaProtocolException("md5 must be 32 hex chars");
        }
        byte[] bytes = new byte[16];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(md5.charAt(i * 2), 16);
            int low = Character.digit(md5.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new FotaProtocolException("md5 contains non-hex chars");
            }
            bytes[i] = (byte) ((high << 4) + low);
        }
        return bytes;
    }


    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }


}
