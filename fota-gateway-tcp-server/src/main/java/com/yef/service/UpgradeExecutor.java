package com.yef.service;

import com.yef.protocol.AckMessage;
import com.yef.protocol.DeviceBootUpMessage;
import com.yef.protocol.FailMessage;
import com.yef.protocol.FotaProtocolConstants;
import com.yef.protocol.HeartbeatMessage;
import com.yef.protocol.UpgradePacketMessage;
import com.yef.protocol.UpgradeRequestMessage;
import com.yef.protocol.UpgradeResultMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UpgradeExecutor {

    private final PacketSender packetSender;
    private final boolean demoUpgradeEnabled;
    private final Map<String, DemoUpgradeTask> demoTasks = new ConcurrentHashMap<>();

    public UpgradeExecutor(PacketSender packetSender,
                           @Value("${gateway.demo-upgrade.enabled:false}") boolean demoUpgradeEnabled) {
        this.packetSender = packetSender;
        this.demoUpgradeEnabled = demoUpgradeEnabled;
    }

    public void onDeviceBootUp(DeviceBootUpMessage message, Long deviceId) {
        System.out.println("[UpgradeExecutor] device boot up, imei=" + message.getImei()
                + ", deviceId=" + deviceId
                + ", firmwareVersion=" + message.getFirmwareVersion()
                + ", deviceType=" + message.getDeviceType());
        resumeIfNeeded(message.getImei(), deviceId);
        startDemoUpgradeIfNeeded(message.getImei());
    }

    public void handleHeartbeat(HeartbeatMessage message, Long deviceId) {
        System.out.println("[UpgradeExecutor] heartbeat, imei=" + message.getImei() + ", deviceId=" + deviceId);
    }

    public void handleAck(AckMessage message, Long deviceId) {
        if (message.getAckType() == FotaProtocolConstants.ACK_TYPE_UPGRADE_REQUEST) {
            System.out.println("[UpgradeExecutor] upgrade request ACK, imei=" + message.getImei()
                    + ", deviceId=" + deviceId + ", taskId=" + message.getTaskId());
            sendNextDemoPacket(message.getImei(), message.getTaskId(), 1);
            return;
        }
        if (message.getAckType() == FotaProtocolConstants.ACK_TYPE_CANCEL) {
            System.out.println("[UpgradeExecutor] cancel upgrade ACK, imei=" + message.getImei()
                    + ", deviceId=" + deviceId + ", taskId=" + message.getTaskId());
            return;
        }
        System.out.println("[UpgradeExecutor] packet ACK, imei=" + message.getImei()
                + ", deviceId=" + deviceId
                + ", taskId=" + message.getTaskId()
                + ", packetNo=" + message.getPacketNo());
        if (message.getAckType() == FotaProtocolConstants.ACK_TYPE_PACKET) {
            sendNextDemoPacket(message.getImei(), message.getTaskId(), message.getPacketNo() + 1);
        }
    }

    public void handleUpgradeResult(UpgradeResultMessage message, Long deviceId) {
        System.out.println("[UpgradeExecutor] upgrade result, imei=" + message.getImei()
                + ", deviceId=" + deviceId
                + ", taskId=" + message.getTaskId()
                + ", result=" + message.getResult()
                + ", errorCode=" + message.getErrorCode()
                + ", costTime=" + message.getCostTime());
        demoTasks.remove(message.getImei());
    }

    public void handleFail(FailMessage message, Long deviceId) {
        System.out.println("[UpgradeExecutor] device FAIL, imei=" + message.getImei()
                + ", deviceId=" + deviceId
                + ", taskId=" + message.getTaskId()
                + ", packetNo=" + message.getPacketNo()
                + ", errorCode=" + message.getErrorCode());
    }

    public void pauseIfUpgrading(String imei, Long deviceId) {
        System.out.println("[UpgradeExecutor] pause upgrade if needed, imei=" + imei + ", deviceId=" + deviceId);
    }

    public void resumeIfNeeded(String imei, Long deviceId) {
        System.out.println("[UpgradeExecutor] resume upgrade if needed, imei=" + imei + ", deviceId=" + deviceId);
    }

    private void startDemoUpgradeIfNeeded(String imei) {
        if (!demoUpgradeEnabled || demoTasks.containsKey(imei)) {
            return;
        }
        byte[] firmware = ("mock-firmware-" + imei + "-" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
        int chunkSize = 16;
        int totalPacket = (firmware.length + chunkSize - 1) / chunkSize;
        long taskId = System.currentTimeMillis();
        DemoUpgradeTask task = new DemoUpgradeTask(taskId, 10001L, firmware, md5(firmware), chunkSize, totalPacket);
        demoTasks.put(imei, task);
        UpgradeRequestMessage message = new UpgradeRequestMessage(imei, task.taskId, task.firmwareId, task.totalPacket,
                task.chunkSize, task.firmware.length, task.md5);
        System.out.println("[UpgradeExecutor] demo send 0x81 UpgradeRequest, imei=" + imei + ", taskId=" + taskId);
        packetSender.sendToDevice(imei, message);
    }

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

    private static class DemoUpgradeTask {
        private final long taskId;
        private final long firmwareId;
        private final byte[] firmware;
        private final byte[] md5;
        private final int chunkSize;
        private final int totalPacket;

        private DemoUpgradeTask(long taskId, long firmwareId, byte[] firmware, byte[] md5, int chunkSize, int totalPacket) {
            this.taskId = taskId;
            this.firmwareId = firmwareId;
            this.firmware = firmware;
            this.md5 = md5;
            this.chunkSize = chunkSize;
            this.totalPacket = totalPacket;
        }
    }
}
