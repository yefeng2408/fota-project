package com.yef.service;

import com.yef.protocol.*;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import com.yef.protocol.outMsg.DeviceBootUpMessageAck;
import com.yef.protocol.outMsg.UpgradeResultMessageAck;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class UpgradeExecutor {

    /**
     * 升级运行态 设备基础信息 设备网关所用的key，用于分包过程中的【高频写操作】
     */
    private static final String UPGRADE_RUNTIME_KEY_PREFIX = "fota:upgrade:runtime:";

    private final StringRedisTemplate redisTemplate;

    private final MinioClient minioClient;

    private final PacketSender packetSender;

    private final boolean demoUpgradeEnabled;

    private final Map<String, DemoUpgradeTask> demoTasks = new ConcurrentHashMap<>();

    public UpgradeExecutor(StringRedisTemplate redisTemplate, MinioClient minioClient,
                           PacketSender packetSender,
                           @Value("${gateway.demo-upgrade.enabled:false}") boolean demoUpgradeEnabled) {
        this.redisTemplate = redisTemplate;
        this.minioClient = minioClient;
        this.packetSender = packetSender;
        this.demoUpgradeEnabled = demoUpgradeEnabled;
    }

    //网关对设备开机包的上行消息0x10做出应答【写出站消息】
    public void onDeviceBootUp(DeviceBootUpMessage msg, Long deviceId) {
        DeviceBootUpMessageAck messageAck = new DeviceBootUpMessageAck(
                msg.imei(),
                0L,
                FotaProtocolConstants.MSG_DEVICE_BOOT_UP,
                (byte) 0x00,
                (byte) 0x00
        );
        packetSender.sendToDevice(msg.imei(), messageAck);

        //resumeIfNeeded(message.getImei(), deviceId);
        //startDemoUpgradeIfNeeded(message.getImei());
    }



    //网关对设备升级结果的上行消息0x06做出应答【写出站消息】
    public void handleUpgradeResult(UpgradeResultMessage message, Long deviceId) {
        System.out.println("[UpgradeExecutor] upgrade result, imei=" + message.imei()
                + ", deviceId=" + deviceId
                + ", taskId=" + message.getTaskId()
                + ", result=" + message.getResult()
                + ", errorCode=" + message.getErrorCode()
                /*+ ", costTime=" + message.getCostTime()*/);
        demoTasks.remove(message.imei());

        UpgradeResultMessageAck messageAck = new UpgradeResultMessageAck(
                message.imei(),
                message.getTaskId(),
                FotaProtocolConstants.MSG_UPGRADE_RESULT,
                (byte) 0x00,
                (byte) 0x00
                );
        packetSender.sendToDevice(messageAck.imei(), messageAck);
        //
        String runtimeKey = UPGRADE_RUNTIME_KEY_PREFIX + message.imei();
        Map<String, String> runtimeHash = new HashMap<>();
        long now = System.currentTimeMillis();
        runtimeHash.put("endAt", String.valueOf(now));
        runtimeHash.put("progress", "100");
        runtimeHash.put("packetTime", String.valueOf(now));
        runtimeHash.put("lastPacketAt", String.valueOf(now));
        runtimeHash.put("status", "SUCCESS");
        redisTemplate.opsForHash().putAll(runtimeKey, runtimeHash);
    }


    public void pauseIfUpgrading(String imei, Long deviceId) {
        System.out.println("[UpgradeExecutor] pause upgrade if needed, imei=" + imei + ", deviceId=" + deviceId);
    }

    public void resumeIfNeeded(String imei, Long deviceId) {
        System.out.println("[UpgradeExecutor] resume upgrade if needed, imei=" + imei + ", deviceId=" + deviceId);
    }

//    private void startDemoUpgradeIfNeeded(String imei) {
//        if (!demoUpgradeEnabled || demoTasks.containsKey(imei)) {
//            return;
//        }
//        byte[] firmware = ("mock-firmware-" + imei + "-" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
//        int chunkSize = 16;
//        int totalPacket = (firmware.length + chunkSize - 1) / chunkSize;
//        long taskId = System.currentTimeMillis();
//        DemoUpgradeTask task = new DemoUpgradeTask(taskId, 10001L, firmware, md5(firmware), chunkSize, totalPacket);
//        demoTasks.put(imei, task);
//        UpgradeRequestMessage message = new UpgradeRequestMessage(imei, task.taskId, task.firmwareId, task.totalPacket,
//                task.chunkSize, task.firmware.length, task.md5);
//        System.out.println("[UpgradeExecutor] demo send 0x81 UpgradeRequest, imei=" + imei + ", taskId=" + taskId);
//        packetSender.sendToDevice(imei, message);
//    }

    private void sendNextDemoPacket(String imei, long taskId, int packetNo) {
        DemoUpgradeTask task = demoTasks.get(imei);
        if (task == null || task.taskId != taskId || packetNo > task.totalPacket) {
            return;
        }
        int from = (packetNo - 1) * task.chunkSize;
        int to = Math.min(from + task.chunkSize, task.firmware.length);
        byte[] chunk = Arrays.copyOfRange(task.firmware, from, to);
        System.out.println("[UpgradeExecutor] demo send 0x82 UpgradePacket, imei=" + imei
                + ", taskId=" + taskId + ", packetNo=" + packetNo + "/" + task.totalPacket);
        packetSender.sendToDevice(imei, new UpgradePacketMessage(imei, taskId, packetNo, task.totalPacket, chunk));
    }

    private byte[] md5(byte[] data) {
        try {
            return MessageDigest.getInstance("MD5").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("md5 failed", e);
        }
    }

    /**
     * 发送分包数据【0x82】
     * @param ack
     */
    public void sendSpiltPacket(AckMessage ack) {
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
            System.out.println("[UpgradeExecutor]---------> send 0x82 UpgradePacket, packetNo=" + packetNo);
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
            int progress = (int) Math.min(100L, ((long) nextPacketNo * 100) / totalPacket);

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
        } catch (Exception e) {
            throw new RuntimeException(
                    "read split packet from minio failed, imei=" + ack.imei()
                            + ", taskId=" + ack.getTaskId()
                            + ", packetNo=" + nextPacketNo,
                    e
            );
        }

    }


    private record DemoUpgradeTask(long taskId,
                                   long firmwareId,
                                   byte[] firmware,
                                   byte[] md5,
                                   int chunkSize,
                                   int totalPacket) {


    }
}
