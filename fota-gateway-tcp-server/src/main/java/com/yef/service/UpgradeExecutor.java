package com.yef.service;

import com.alibaba.fastjson.JSON;
import com.yef.protocol.*;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import com.yef.protocol.outMsg.DeviceBootUpMessageAck;
import com.yef.protocol.outMsg.UpgradeResultMessageAck;
import com.yef.req.DeviceUpgradeCancelEventRequest;
import com.yef.req.DeviceUpgradeEventRequest;
import com.yef.req.DeviceUpgradeStartTimeRequest;
import com.yef.service.redis.semaphore.UpgradeSemaphoreReleaseService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
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
    private final UpgradeSemaphoreReleaseService upgradeSemaphoreReleaseService;


    public UpgradeExecutor(StringRedisTemplate redisTemplate,
                           MinioClient minioClient,
                           PacketSender packetSender,
                           DeviceUpgradeEventPushClient deviceUpgradeEventPushClient,
                           DeviceUpgradeLockService deviceUpgradeLockService,
                           UpgradeSemaphoreReleaseService upgradeSemaphoreReleaseService) {
        this.redisTemplate = redisTemplate;
        this.minioClient = minioClient;
        this.packetSender = packetSender;
        this.deviceUpgradeEventPushClient = deviceUpgradeEventPushClient;
        this.deviceUpgradeLockService = deviceUpgradeLockService;
        this.upgradeSemaphoreReleaseService = upgradeSemaphoreReleaseService;
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
    public void handleUpgradeResult(UpgradeResultMessage message) {
        log.info("[UpgradeExecutor] upgrade result. imei:{} ,taskId:{}, result:{}, errorCode:{}", message.imei(), message.getTaskId(), message.getResult(), message.getErrorCode());
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
        String lockToken = runtimeMap == null ? null : String.valueOf(runtimeMap.get("lockToken"));
        String targetFirmwareVersion = runtimeMap == null ? null : String.valueOf(runtimeMap.get("targetFirmwareVersion"));
        String finalStatus = message.getResult() == 0 ? "SUCCESS" : "FAIL";

        Map<String, String> runtimeHash = new HashMap<>();
        long now = System.currentTimeMillis();
        runtimeHash.put("endAt", String.valueOf(now));
        runtimeHash.put("progress", "100");
        runtimeHash.put("packetTime", String.valueOf(now));
        runtimeHash.put("lastPacketAt", String.valueOf(now));
        runtimeHash.put("status", finalStatus);
        runtimeHash.put("costTime", String.valueOf(message.getCostTime()));
        redisTemplate.opsForHash().putAll(runtimeKey, runtimeHash);
        //释放锁
        if (lockToken != null && !lockToken.isBlank() && !"null".equalsIgnoreCase(lockToken)) {
            deviceUpgradeLockService.releaseLock(message.imei(), lockToken);
        }
        deviceUpgradeLockService.clearActive(message.imei());
        //释放信号量
        upgradeSemaphoreReleaseService.release(message.imei());

        deviceUpgradeEventPushClient.pushUpgradeProgress(
                new DeviceUpgradeEventRequest(
                        message.imei(),
                        finalStatus,
                        100,
                        null,
                        null
                )
        );

        deviceUpgradeEventPushClient.updateFinalUpgradeTaskRecord(
                new com.yef.req.UpdateDeviceUpgradeFinalResult(
                        message.imei(),
                        String.valueOf(message.getTaskId()),
                        finalStatus,
                        100,
                        null,
                        targetFirmwareVersion,
                        0,
                        0,
                        finalStatus.equals("SUCCESS") ? null : String.valueOf(message.getErrorCode()),
                        null,
                        java.time.LocalDateTime.now()
                )
        );

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

        Integer packetNo = Integer.valueOf(String.valueOf(runtimeMap.get("packetNo")));
        Integer totalPacket = Integer.valueOf(String.valueOf(runtimeMap.get("totalPacket")));
        Integer chunkSize = Integer.valueOf(String.valueOf(runtimeMap.get("chunkSize")));
        Long fileSize = Long.valueOf(String.valueOf(runtimeMap.get("fileSize")));
        String bucketName = String.valueOf(runtimeMap.get("bucketName"));
        String objectName = String.valueOf(runtimeMap.get("objectName"));

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

        long offset = (long) (nextPacketNo - 1) * chunkSize;
        long length = Math.min(chunkSize, fileSize - offset);
        if (length <= 0) {
            return;
        }

        //按 offset读取
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .offset(offset)
                        .length(length)
                        .build())) {

            byte[] chunk = in.readAllBytes();

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
            if(nextPacketNo==1){
                DeviceUpgradeStartTimeRequest startTimeRequest = new DeviceUpgradeStartTimeRequest(runtimeTaskId,LocalDateTime.now());
                deviceUpgradeEventPushClient.updateStartTime(startTimeRequest);
            }

            /*
             * WebSocketConfig 这个 bean 不能直接注册到 UpgradeExecutor，因为它们属于两个独立服务；
             * 正确做法是让 gateway 在 sendSpiltPacket 里把升级事件通过内部 HTTP 接口发送给 fota-web-http-server，
             * 再由 web-http-server 的 WebSocketConfig 广播给前端。
             *
             * 在设备升级过程中，由于分包高频推进，progress 计算存在重复值，为避免 WebSocket 推送风暴，
             * 通过 Redis 记录上一次推送的进度值，并结合时间窗口做限流控制，仅在进度发生变化且满足时间阈值时才触发推送，
             * 从而实现高频场景下的稳定推送机制。
             */
            String lastProgressKey = runtimeKey + ":lastPushProgress";

            String lastProgressStr = redisTemplate.opsForValue().get(lastProgressKey);
            int lastPushProgress = lastProgressStr == null ? -1 : Integer.parseInt(lastProgressStr);

            if (progress != lastPushProgress) {
                // 更新已推送进度
                redisTemplate.opsForValue().set(lastProgressKey, String.valueOf(progress));
                String upgradeStatus = nextPacketNo >= totalPacket ? "WAIT_RESULT" : "UPGRADING";
                // 推送进度条 progress
                deviceUpgradeEventPushClient.pushUpgradeProgress(
                        new DeviceUpgradeEventRequest(
                                ack.imei(),
                                upgradeStatus,
                                progress,
                                null,
                                null
                        )
                );
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "read split packet from minio failed, imei=" + ack.imei()
                            + ", taskId=" + ack.getTaskId()
                            + ", packetNo=" + nextPacketNo,
                    e
            );
        }

    }


    //
    public void receiveCancelAck(AckMessage ack) {
        log.info("=======>device-upgrade-event-ack:{}", JSON.toJSONString(ack));
        String runtimeKey = UPGRADE_RUNTIME_KEY_PREFIX + ack.imei();
        redisTemplate.opsForHash().put(runtimeKey,"status","CANCEL_UPGRADE");

        DeviceUpgradeCancelEventRequest request = new DeviceUpgradeCancelEventRequest();
        request.setImei(ack.imei());
        request.setStatus("CANCEL_UPGRADE");
        deviceUpgradeEventPushClient.updateCancelResult(request);
    }
}
