package com.yef.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;

public final class ProtocolBodyUtils {

    private ProtocolBodyUtils() {
    }

    public static String readImei(ByteBuf in) {
        return Long.toUnsignedString(in.readLong());
    }

    public static void writeImei(ByteBuf out, String imei) {
        out.writeLong(Long.parseUnsignedLong(imei));
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
    public static byte[] buildCrcPayload(byte version, byte messageType, byte[] body) {
        ByteBuf buf = Unpooled.buffer(1 + 4 + 1 + body.length);
        try {
            buf.writeByte(version);
            buf.writeInt(body.length);
            buf.writeByte(messageType);
            buf.writeBytes(body);
            return toByteArray(buf);
        } finally {
            buf.release();
        }
    }
}
