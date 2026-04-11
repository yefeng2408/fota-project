package com.yef.service;

import com.yef.protocol.AckMessage;
import com.yef.protocol.DeviceRegisterMessage;
import com.yef.protocol.FailMessage;
import org.springframework.stereotype.Service;

@Service
public class UpgradeExecutor {

    public void onDeviceRegistered(DeviceRegisterMessage message, Long deviceId) {
        System.out.println("[UpgradeExecutor] device registered, imei=" + message.getImei()
                + ", deviceId=" + deviceId
                + ", firmwareVersion=" + message.getFirmwareVersion()
                + ", deviceType=" + message.getDeviceType());
        resumeIfNeeded(message.getImei(), deviceId);
    }

    public void handleAck(AckMessage message, Long deviceId) {
        if (message.getPacketNo() == 0) {
            System.out.println("[UpgradeExecutor] upgrade request ACK, imei=" + message.getImei()
                    + ", deviceId=" + deviceId + ", taskId=" + message.getTaskId());
            return;
        }
        System.out.println("[UpgradeExecutor] packet ACK, imei=" + message.getImei()
                + ", deviceId=" + deviceId
                + ", taskId=" + message.getTaskId()
                + ", packetNo=" + message.getPacketNo());
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
}
