package com.yef.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public final class FotaProtocol {

    public static final byte HEAD = 0x5B;
    public static final byte TAIL = 0x5D;
    public static final byte VERSION = 0x01;
    public static final byte ACK = 0x03;
    public static final byte FAIL = 0x04;
    public static final byte HEARTBEAT = 0x05;
    public static final byte UPGRADE_RESULT = 0x06;
    public static final byte DEVICE_BOOT_UP = 0x10;
    public static final byte UPGRADE_REQUEST = (byte) 0x81;
    public static final byte UPGRADE_PACKET = (byte) 0x82;
    public static final byte PLATFORM_ACK = (byte) 0x83;
    public static final byte CANCEL_UPGRADE = (byte) 0x87;
    public static final byte ACK_TYPE_UPGRADE_REQUEST = 1;
    public static final byte ACK_TYPE_PACKET = 2;
    public static final byte ACK_TYPE_CANCEL = 4;
    public static final int MIN_FRAME_LENGTH = 28;
    public static final int MAX_BODY_LENGTH = 1024 * 1024;
    public static final int MAX_FRAME_LENGTH = MIN_FRAME_LENGTH + MAX_BODY_LENGTH;
    public static final int LENGTH_FIELD_OFFSET = 2;
    public static final int LENGTH_FIELD_LENGTH = 4;
    public static final int LENGTH_ADJUSTMENT = 8 + 8 + 2 + 1 + 2 + 1;
    public static final int INITIAL_BYTES_TO_STRIP = 0;

    private FotaProtocol() {
    }

    public interface Message {
        byte messageType();

        String imei();
    }

    public record Frame(byte version, String imei, long timestamp, int seqId, byte messageType, byte[] body, int crc16) {
    }

    public record DeviceBootUpDTO(String imei, String firmwareVersion, String deviceType) implements Message {
        @Override
        public byte messageType() {
            return DEVICE_BOOT_UP;
        }
    }

    public record Heartbeat(String imei) implements Message {
        @Override
        public byte messageType() {
            return HEARTBEAT;
        }
    }

    //升级包DTO
    public record UpgradeRequestDTO(String imei,
                                 long taskId,
                                 long firmwareId,
                                 String firmwareName,
                                 String firmwareVersionName,
                                 int totalPacket,
                                 int chunkSize,
                                 long fileSize,
                                 byte[] md5) implements Message {
        @Override
        public byte messageType() {
            return UPGRADE_REQUEST;
        }

        public byte[] md5() {
            return Arrays.copyOf(md5, md5.length);
        }
    }


    public record UpgradePacketDTO(String imei, long taskId, int packetNo, int totalPacket,
                                byte[] chunkData) implements Message {
        @Override
        public byte messageType() {
            return UPGRADE_PACKET;
        }

        public byte[] chunkData() {
            return Arrays.copyOf(chunkData, chunkData.length);
        }
    }

    /**
     *
     * @param imei
     * @param taskId
     * @param packetNo
     * @param ackType
     */
    public record Ack(String imei, long taskId, int packetNo, byte ackType) implements Message {
        @Override
        public byte messageType() {
            return ACK;
        }
    }

    public record Fail(String imei, long taskId, int packetNo, int errorCode) implements Message {
        @Override
        public byte messageType() {
            return FAIL;
        }
    }


    /**
     * 0x06类型消息
     * @param imei
     * @param taskId
     * @param result 0成功 1失败
     * @param errorCode 0正常，其他值表示失败原因
     * @param costTime 升级过程中的分包总耗时。单位：秒
     */
    public record UpgradeResultDTO(String imei, long taskId, byte result, int errorCode, int costTime) implements Message {
        @Override
        public byte messageType() {
            return UPGRADE_RESULT;
        }
    }

    public record PlatformAckDTO(String imei, long taskId, byte refMessageType, byte ackStatus, byte reasonCode) implements Message {
        @Override
        public byte messageType() {
            return PLATFORM_ACK;
        }
    }

    public record CancelUpgradeDTO(String imei, long taskId, byte reason) implements Message {
        @Override
        public byte messageType() {
            return CANCEL_UPGRADE;
        }
    }

    public static String readFixedImei(ByteBuf in) {
        byte[] bytes = new byte[8];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    public static void writeFixedImei(ByteBuf out, String imei) {
        if (imei == null || imei.length() != 8) {
            throw new IllegalArgumentException("imei must be 8 ascii chars: " + imei);
        }
        out.writeBytes(imei.getBytes(StandardCharsets.US_ASCII));
    }

    public static String readStringWithByteLength(ByteBuf in) {
        int length = in.readUnsignedByte();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeStringWithByteLength(ByteBuf out, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 255) {
            throw new IllegalArgumentException("string too long for uint8 length");
        }
        out.writeByte(bytes.length);
        out.writeBytes(bytes);
    }

    public static byte[] buildCrcPayload(byte version, int bodyLength, String imei, long timestamp, int seqId,
                                         byte messageType, byte[] body) {
        ByteBuf buf = Unpooled.buffer(1 + 4 + 8 + 8 + 2 + 1 + body.length);
        try {
            buf.writeByte(version);
            buf.writeInt(bodyLength);
            writeFixedImei(buf, imei);
            buf.writeLong(timestamp);
            buf.writeShort(seqId);
            buf.writeByte(messageType);
            buf.writeBytes(body);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }

    public static int crc16(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc ^= b & 0xFF;
            for (int i = 0; i < 8; i++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    public static byte[] md5(byte[] data) {
        try {
            return MessageDigest.getInstance("MD5").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("md5 failed", e);
        }
    }
}
