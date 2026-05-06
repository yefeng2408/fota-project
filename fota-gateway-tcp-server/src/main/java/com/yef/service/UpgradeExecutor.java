package com.yef.service;

import com.alibaba.fastjson.JSON;
import com.yef.cache.FirmwareCacheHolder;
import com.yef.cache.FirmwareCacheManager;
import com.yef.producer.DeviceUpgradeEventPushClient;
import com.yef.protocol.*;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.yef.protocol.outMsg.DeviceBootUpMessageAck;
import com.yef.protocol.outMsg.UpgradeResultMessageAck;
import com.yef.req.EntryUpgradingEventRequest;
import com.yef.req.UpgradeCancelEventRequest;
import com.yef.req.UpgradeFinalResultEventRequest;
import com.yef.req.UpgradeProgressEventRequest;
import com.yef.req.UpgradeStartTimeEventRequest;
import com.yef.semaphore.UpgradeSemaphoreService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.EventExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UpgradeExecutor {

    /**
     * 升级运行态 设备基础信息 设备网关所用的key，用于分包过程中的【高频写操作】
     */
    private static final String UPGRADE_RUNTIME_KEY_PREFIX = "fota:upgrade:runtime:";

    private final StringRedisTemplate redisTemplate;

    private final MinioClient minioClient;

    private final PacketSender packetSender;

    private final DeviceUpgradeEventPushClient deviceUpgradeEventPushClient;
    private final DeviceUpgradeLockService deviceUpgradeLockService;
    private final FirmwareCacheManager firmwareCacheManager;


    public UpgradeExecutor(StringRedisTemplate redisTemplate,
                           MinioClient minioClient,
                           PacketSender packetSender,
                           DeviceUpgradeEventPushClient deviceUpgradeEventPushClient,
                           DeviceUpgradeLockService deviceUpgradeLockService,
                           FirmwareCacheManager firmwareCacheManager) {
        this.redisTemplate = redisTemplate;
        this.minioClient = minioClient;
        this.packetSender = packetSender;
        this.deviceUpgradeEventPushClient = deviceUpgradeEventPushClient;
        this.deviceUpgradeLockService = deviceUpgradeLockService;
        this.firmwareCacheManager = firmwareCacheManager;
    }

    //网关对设备开机包的上行消息0x10做出应答【写出站消息】
    public void onDeviceBootUp(DeviceBootUpMessage msg) {
        DeviceBootUpMessageAck messageAck = new DeviceBootUpMessageAck(
                msg.imei(),
                0L,
                FotaProtocolConstants.MSG_DEVICE_BOOT_UP,
                (byte) 0x00,
                (byte) 0x00
        );
        packetSender.sendToDevice(msg.imei(), messageAck);

    }


    //网关对设备升级结果的上行消息0x06做出应答【写出站消息】
    public void receiveUpgradeResult(UpgradeResultMessage message) {
        log.debug("+++++++++++++++++>>>>>>>>>[UpgradeExecutor] upgrade result UpgradeResultMessage= {}", JSON.toJSONString(message));
        UpgradeResultMessageAck messageAck = new UpgradeResultMessageAck(
                message.imei(),
                message.getTaskId(),
                FotaProtocolConstants.MSG_UPGRADE_RESULT,
                (byte) 0x00,
                (byte) 0x00
        );
        packetSender.sendToDevice(messageAck.imei(), messageAck);

        String runtimeKey = UPGRADE_RUNTIME_KEY_PREFIX + message.imei();
        Map<Object, Object> runtimeMap = redisTemplate.opsForHash().entries(runtimeKey);
        String targetFirmwareVersion = runtimeMap == null ? null : String.valueOf(runtimeMap.get("targetFirmwareVersion"));
        String finalStatus = message.getResult() == 0 ? "SUCCESS" : "FAIL";
        long now = System.currentTimeMillis();

        Map<String, String> runtimeHash = new HashMap<>();
        runtimeHash.put("endAt", String.valueOf(now));
        runtimeHash.put("progress", "100");
        runtimeHash.put("packetTime", String.valueOf(now));
        runtimeHash.put("lastPacketAt", String.valueOf(now));
        runtimeHash.put("status", finalStatus);
        runtimeHash.put("costTime", String.valueOf(message.getCostTime()));
        redisTemplate.opsForHash().putAll(runtimeKey, runtimeHash);

        deviceUpgradeEventPushClient.pushUpgradeProgress(
                new UpgradeProgressEventRequest(
                        message.imei(),
                        message.getTaskId(),
                        finalStatus,
                        100,
                        null,
                        null
                )
        );

        deviceUpgradeEventPushClient.updateFinalUpgradeTaskRecord(
                new UpgradeFinalResultEventRequest(
                        message.imei(),
                        String.valueOf(message.getTaskId()),
                        finalStatus,
                        100,
                        null,
                        targetFirmwareVersion,
                        0,
                        0,
                        finalStatus.equals("SUCCESS") ? null : String.valueOf(message.getErrorCode()),
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
                )
        );
        // 释放锁
        deviceUpgradeLockService.releaseLock(message.imei(), String.valueOf(message.getTaskId()));
        deviceUpgradeLockService.clearActive(message.imei());

        Object firmwareIdObj = runtimeMap.get("firmwareId");
        Object objectName = runtimeMap.get("objectName");
        FirmwareCacheHolder firmwareCacheHolder = firmwareCacheManager.get(Long.valueOf(firmwareIdObj.toString()));
        if(firmwareCacheHolder!=null){
            firmwareCacheHolder.getRefCount().decrementAndGet();
            //log.info("0x81 ack===>固件引用计数减1。当前计数refCount={}, objectName={}",firmwareCacheHolder.getRefCount().get(), objectName);
            if(firmwareCacheHolder.getRefCount().get()==0){
                //释放固件内存数据
                firmwareCacheManager.remove(Long.valueOf(firmwareIdObj.toString()));
                log.info("0x81 ack===>固件引用计数为0，释放固件内存。objectName={}", objectName);
            }
        }
    }


    /**
     * 发送分包数据【0x82】
     *
     * @param ack
     */
    public void receiveUpgradeRequestAckAndSendSpiltPacket(AckMessage ack) {
        String runtimeKey = UPGRADE_RUNTIME_KEY_PREFIX + ack.imei();
        Map<Object, Object> runtimeMap = redisTemplate.opsForHash().entries(runtimeKey);
        if (runtimeMap == null || runtimeMap.isEmpty()) {
            return;
        }

        Long runtimeTaskId = Long.valueOf(String.valueOf(runtimeMap.get("taskId")));
        if (!Objects.equals(ack.getTaskId(), runtimeTaskId)) {
            return;
        }

        Long firmwareId = Long.valueOf(String.valueOf(runtimeMap.get("firmwareId")));
        FirmwareCacheHolder firmwareCacheHolder = firmwareCacheManager.get(firmwareId);
        if (firmwareCacheHolder == null) {
            log.warn("固件不存在，无法进行0x82分包下发过程！");
            return;
        }

        Integer packetNo = Integer.valueOf(String.valueOf(runtimeMap.get("packetNo")));
        Integer totalPacket = Integer.valueOf(String.valueOf(runtimeMap.get("totalPacket")));
        Integer chunkSize = Integer.valueOf(String.valueOf(runtimeMap.get("chunkSize")));
        Long fileSize = Long.valueOf(String.valueOf(runtimeMap.get("fileSize")));

        int nextPacketNo;

        if (ack.getAckType() == FotaProtocolConstants.ACK_TYPE_UPGRADE_REQUEST && packetNo == 0) {
            nextPacketNo = 1;
        } else if (ack.getAckType() == FotaProtocolConstants.ACK_TYPE_PACKET) {
            nextPacketNo = ack.getPacketNo() + 1;
            //log.info("[UpgradeExecutor]---------> send 0x82 UpgradePacket, packetNo:{}", packetNo);
        } else {
            return;
        }

        if (nextPacketNo > totalPacket) {
            return;
        }

        if (nextPacketNo == 1) {
            EntryUpgradingEventRequest request = new EntryUpgradingEventRequest(
                    ack.imei(),
                    runtimeTaskId,
                    "UPGRADING"
            );
            deviceUpgradeEventPushClient.pushEntryIntoUpgradingStatus(request);
        }

        int offset = (nextPacketNo - 1) * chunkSize;
        int length = Math.toIntExact(Math.min(chunkSize, fileSize - offset));
        if (length <= 0) {
            return;
        }

        //按 offset 读取
        byte[] bytes = firmwareCacheHolder.getFirmwareFullBytes();
        byte[] chunk = Arrays.copyOfRange(bytes, offset, offset + length);

        packetSender.sendToDevice(
                ack.imei(),
                new UpgradePacketMessage(
                        ack.imei(),
                        ack.getTaskId(),
                        nextPacketNo,
                        totalPacket,
                        chunk
                )
        );

        long now = System.currentTimeMillis();
        int progress = (int) Math.min(100L, (nextPacketNo * 100) / totalPacket);

        Map<String, String> runtimeHash = new HashMap<>();
        runtimeHash.put("offset", String.valueOf(offset));
        runtimeHash.put("packetNo", String.valueOf(nextPacketNo));
        runtimeHash.put("packetTime", String.valueOf(now));
        runtimeHash.put("lastPacketAt", String.valueOf(now));
        runtimeHash.put("progress", String.valueOf(progress));
        runtimeHash.put("status", nextPacketNo >= totalPacket ? "WAIT_RESULT" : "UPGRADING");
        runtimeHash.put("currentChunkLength", String.valueOf(chunk.length));
        runtimeHash.put("chunkSize", String.valueOf(chunkSize));
        runtimeHash.put("totalPacket", String.valueOf(totalPacket));
        redisTemplate.opsForHash().putAll(runtimeKey, runtimeHash);

        //首次下发分包。推送升级开始时间
        if (nextPacketNo == 1) {
            UpgradeStartTimeEventRequest eventRequest = new UpgradeStartTimeEventRequest(ack.imei(), runtimeTaskId, LocalDateTime.now());
            deviceUpgradeEventPushClient.updateStartTime(eventRequest);
        }

        /*
         * 在设备升级过程中，由于分包高频推进，progress 计算存在重复值，为避免 WebSocket 推送风暴，
         * 通过 Redis 记录上一次推送的进度值，并结合时间窗口做限流控制，仅在进度发生变化且满足时间阈值时才触发推送，
         * 从而实现高频场景下的稳定推送机制。
         */
        String lastProgressKey = runtimeKey + ":lastPushProgress";

        String lastProgressStr = redisTemplate.opsForValue().get(lastProgressKey);
        int lastPushProgress = lastProgressStr == null ? -1 : Integer.parseInt(lastProgressStr);

        int pushProgress = calcPushProgress(progress);
        if (pushProgress > lastPushProgress) {
            // 更新已推送进度：只推送 10%、20%、30% ... 100%，降低 MQ / WebSocket 压力
            redisTemplate.opsForValue().set(lastProgressKey, String.valueOf(pushProgress));
            String upgradeStatus = nextPacketNo >= totalPacket ? "WAIT_RESULT" : "UPGRADING";
            deviceUpgradeEventPushClient.pushUpgradeProgress(
                    new UpgradeProgressEventRequest(
                            ack.imei(),
                            ack.getTaskId(),
                            upgradeStatus,
                            pushProgress,
                            null,
                            null
                    )
            );
        }

    }


    /**
     * handle 上行0x06
     *
     * @param ack
     */
    public void receiveCancelAck(AckMessage ack) {
        log.info("111=======>device-upgrade-event-ack:{}", JSON.toJSONString(ack));
        String runtimeKey = UPGRADE_RUNTIME_KEY_PREFIX + ack.imei();
        Object firmwareIdObj = redisTemplate.opsForHash().get(runtimeKey, "firmwareId");
        String objectName = redisTemplate.opsForHash().get(runtimeKey, "objectName").toString();
        redisTemplate.opsForHash().put(runtimeKey, "status", "CANCEL_UPGRADE");

        UpgradeCancelEventRequest request = new UpgradeCancelEventRequest();
        request.setImei(ack.imei());
        request.setUpgradeStatus("CANCEL_UPGRADE");
        String lockToken = String.valueOf(redisTemplate.opsForHash().get(runtimeKey, "lockToken"));
        if (!Objects.equals(lockToken, "null") && !lockToken.isBlank()) {
            deviceUpgradeLockService.releaseLock(ack.imei(), lockToken);
        } else if (ack.getTaskId() != null) {
            deviceUpgradeLockService.releaseLock(ack.imei(), String.valueOf(ack.getTaskId()));
        }
        deviceUpgradeLockService.clearActive(ack.imei());
        deviceUpgradeEventPushClient.updateCancelResult(request);

        FirmwareCacheHolder firmwareCacheHolder = firmwareCacheManager.get(Long.valueOf(firmwareIdObj.toString()));
        if(firmwareCacheHolder!=null){
            firmwareCacheHolder.getRefCount().decrementAndGet();
            if(firmwareCacheHolder.getRefCount().get()==0){
                //释放固件内存数据
                firmwareCacheManager.remove(Long.valueOf(firmwareIdObj.toString()));
                log.info("0x87 ack===>固件引用计数为0，释放固件内存。objectName={}", objectName);
            }
        }

    }


    /**
     * 将高频分包进度压缩成 10% 粒度，避免每 1% 都投递 MQ / 推送 WebSocket。
     * 例如：1~9 不推送，10~19 推送 10，20~29 推送 20，最终强制推送 100。
     */
    private int calcPushProgress(int progress) {
        if (progress >= 100) {
            return 100;
        }
        if (progress < 10) {
            return 0;
        }
        return (progress / 10) * 10;
    }



}


