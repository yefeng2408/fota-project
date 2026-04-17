package com.yef.protocol.out;

import com.yef.protocol.FotaMessage;
import com.yef.protocol.FotaProtocolConstants;

/**
 * @description: 网关给设备的开机包 ack
 * @author: 叶丰
 * @date: 2026/04/17 16:22
 */
public record DeviceBootUpMessageAck(
        String imei,
        long taskId,
        byte refMessageType,
        byte ackStatus,
        byte reasonCode

) implements FotaMessage {
    @Override
    public byte getMessageType() {
        return FotaProtocolConstants.PLATFORM_ACK;
    }

    @Override
    public String imei() {
        return imei;
    }

    @Override
    public Long getTaskId() {
        return taskId;
    }
}
