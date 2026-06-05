package com.yef.service;

import com.yef.cache.FirmwareCacheHolder;
import com.yef.cache.FirmwareCacheLoadService;
import com.yef.dto.PlatformCancelUpgradeRequest;
import com.yef.dto.PlatformUpgradeRequest;
import com.yef.exception.FotaProtocolException;
import com.yef.protocol.CancelUpgradeMessage;
import com.yef.protocol.UpgradeRequestMessage;
import com.yef.session.DeviceSession;
import com.yef.session.SessionManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class GatewayUpgradeDispatchService {

    /**
     * 升级运行态 设备基础信息 设备网关所用的key，用于分包过程中的【高频写操作】
     */
    private static final String UPGRADE_RUNTIME_KEY_PREFIX = "fota:upgrade:runtime:";

    private final SessionManager sessionManager;
    private final StringRedisTemplate redisTemplate;
    private final DeviceUpgradeLockService deviceUpgradeLockService;
    private final FirmwareCacheLoadService firmwareCacheLoadService;

    public GatewayUpgradeDispatchService(SessionManager sessionManager,
                                         StringRedisTemplate redisTemplate,
                                         DeviceUpgradeLockService deviceUpgradeLockService,
                                         FirmwareCacheLoadService firmwareCacheLoadService) {
        this.sessionManager = sessionManager;
        this.redisTemplate = redisTemplate;
        this.deviceUpgradeLockService = deviceUpgradeLockService;
        this.firmwareCacheLoadService = firmwareCacheLoadService;
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

            Future<FirmwareCacheHolder> future = firmwareCacheLoadService.loadFirmwarePackageToLocalCache(runtimeHash);
            FirmwareCacheHolder firmwareCacheHolder = future.get(10, TimeUnit.SECONDS);
            if (firmwareCacheHolder == null || !req.getFirmwareId().equals(firmwareCacheHolder.getFirmwareId())) {
                throw new FotaProtocolException("固件缓存加载失败，拒绝下发升级请求");
            }

            Channel channel = session.getChannel();
            ChannelFuture channelFuture = channel.writeAndFlush(message);
            channelFuture.addListener(fu -> {
                if (fu.isSuccess()) {
                    deviceUpgradeLockService.markActive(req.getImei());
                } else {
                    deviceUpgradeLockService.releaseLock(req.getImei(), req.getLockToken());
                }
            });
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
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

        runtimeHash.put("startAt", String.valueOf(0));
        runtimeHash.put("endAt", String.valueOf(0));
        runtimeHash.put("progress", String.valueOf(0));

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


    public void sendCancelUpgradeRequest(PlatformCancelUpgradeRequest request) {
        log.info("------>取消升级接受http入参sendCancelUpgradeRequest，taskId:{}", request.getTaskId());
        DeviceSession session = sessionManager.getByImei(request.getImei());
        if (session == null || session.getChannel() == null || !session.getChannel().isActive()) {
            throw new FotaProtocolException("设备不在线，无法下发取消升级指令");
        }
        //组装 CancelUpgradeMessage(0x87)，然后触发写出站事件 writeAndFlush
        CancelUpgradeMessage message = new CancelUpgradeMessage(request.getImei(), request.getTaskId(), request.getReason());

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
