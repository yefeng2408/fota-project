package com.yef.protocol.outMsg;

import com.yef.protocol.FotaMessage;
import com.yef.protocol.FotaProtocolConstants;

/**
 * @description: 固件分包数据
 * @author: 叶丰
 * @date: 2026/4/19 22:41
 */
public record SpiltPacket(
        String imei,
        Long taskId,
        int packetNo,
        int totalPacket,
        int chunkLength,
        byte[] chunkData

) implements FotaMessage {
    @Override
    public byte getMessageType() {
        return FotaProtocolConstants.MSG_UPGRADE_PACKET;
    }

    @Override
    public Long getTaskId() {
        return taskId;
    }
}
