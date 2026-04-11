package com.yef.protocol;

import java.util.Arrays;

public class UpgradeRequestMessage implements FotaMessage {

    private final String imei;
    private final long taskId;
    private final long firmwareId;
    private final int totalPacket;
    private final int chunkSize;
    private final long fileSize;
    private final byte[] md5;

    public UpgradeRequestMessage(String imei, long taskId, long firmwareId, int totalPacket,
                                 int chunkSize, long fileSize, byte[] md5) {
        this.imei = imei;
        this.taskId = taskId;
        this.firmwareId = firmwareId;
        this.totalPacket = totalPacket;
        this.chunkSize = chunkSize;
        this.fileSize = fileSize;
        this.md5 = md5 == null ? new byte[16] : Arrays.copyOf(md5, md5.length);
    }

    @Override
    public byte getMessageType() {
        return FotaProtocolConstants.MSG_UPGRADE_REQUEST;
    }

    @Override
    public String getImei() {
        return imei;
    }

    @Override
    public Long getTaskId() {
        return taskId;
    }

    public long getFirmwareId() {
        return firmwareId;
    }

    public int getTotalPacket() {
        return totalPacket;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public long getFileSize() {
        return fileSize;
    }

    public byte[] getMd5() {
        return Arrays.copyOf(md5, md5.length);
    }
}
