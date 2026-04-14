package com.yef.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;

public final class ProtocolBodyUtils {

    private ProtocolBodyUtils() {
    }


    public static String readFixedImei(ByteBuf in) {
        byte[] bytes = new byte[8];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    public static void writeFixedImei(ByteBuf out, String imei) {
        String value = imei == null ? "" : imei;
        if (value.length() != 8) {
            throw new IllegalArgumentException("imei must be 8 ascii chars: " + value);
        }
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }

    public static String readUtf8WithShortLength(ByteBuf in) {
        int length = in.readUnsignedShort();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeUtf8WithShortLength(ByteBuf out, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65535) {
            throw new IllegalArgumentException("string too long");
        }
        out.writeShort(bytes.length);
        out.writeBytes(bytes);
    }

    public static byte[] toByteArray(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    /**
     *
     * @param version
     * @param messageType
     * @param body
     * @return
     */
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
            return toByteArray(buf);
        } finally {
            buf.release();
        }
    }
}
