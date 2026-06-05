package com.yef.protocol.outMsg;

import com.yef.protocol.FotaMessage;
import com.yef.protocol.FotaProtocolConstants;

/**
 * @description: 平台通用应答
 * @author: yefeng
 * @date: 2026/06/05 16:37
 */
public record PlatformCommonAck (
        String imei,
        Long taskId,
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
        return this.imei;
    }

    @Override
    public Long getTaskId() {
        return this.taskId;
    }
}