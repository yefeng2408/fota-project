package com.yef.protocol;

import java.util.Arrays;

public class UpgradePacketMessage implements FotaMessage {

    private final String imei;
    private final long taskId;
    private final int packetNo;
    private final int totalPacket;
    private final byte[] chunkData;

    public UpgradePacketMessage(String imei, long taskId, int packetNo, int totalPacket, byte[] chunkData) {
        this.imei = imei;
        this.taskId = taskId;
        this.packetNo = packetNo;
        this.totalPacket = totalPacket;
        this.chunkData = chunkData == null ? new byte[0] : Arrays.copyOf(chunkData, chunkData.length);
    }

    @Override
    public byte getMessageType() {
        return FotaProtocolConstants.MSG_UPGRADE_PACKET;
    }

    @Override
    public String getImei() {
        return imei;
    }

    @Override
    public Long getTaskId() {
        return taskId;
    }

    public int getPacketNo() {
        return packetNo;
    }

    public int getTotalPacket() {
        return totalPacket;
    }

    public int getChunkLength() {
        return chunkData.length;
    }

    public byte[] getChunkData() {
        return Arrays.copyOf(chunkData, chunkData.length);
    }
}
