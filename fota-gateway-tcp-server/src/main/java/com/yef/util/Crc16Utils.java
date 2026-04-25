package com.yef.util;

import io.netty.buffer.ByteBuf;

public final class Crc16Utils {

    private static final int INITIAL = 0xFFFF;
    private static final int POLYNOMIAL = 0xA001;

    private Crc16Utils() {
    }

    /**
     * 计算crc16
     * @param data
     * @return 返回crc16值
     */
    public static int calculate(byte[] data) {
        return update(INITIAL, data);
    }

    public static int calculate(ByteBuf data) {
        return update(INITIAL, data);
    }

    public static int update(int crc, byte[] data) {
        if (data == null) {
            return crc & 0xFFFF;
        }
        for (byte b : data) {
            crc = updateByte(crc, b);
        }
        return crc & 0xFFFF;
    }

    public static int update(int crc, ByteBuf data) {
        if (data == null) {
            return crc & 0xFFFF;
        }
        for (int i = data.readerIndex(); i < data.writerIndex(); i++) {
            crc = updateByte(crc, data.getByte(i));
        }
        return crc & 0xFFFF;
    }

    public static int updateByte(int crc, int value) {
        crc ^= value & 0xFF;
        for (int i = 0; i < 8; i++) {
            if ((crc & 0x0001) != 0) {
                crc = (crc >>> 1) ^ POLYNOMIAL;
            } else {
                crc >>>= 1;
            }
        }
        return crc & 0xFFFF;
    }

    public static int updateShort(int crc, int value) {
        crc = updateByte(crc, (value >>> 8) & 0xFF);
        crc = updateByte(crc, value & 0xFF);
        return crc & 0xFFFF;
    }

    public static int updateInt(int crc, int value) {
        crc = updateByte(crc, (value >>> 24) & 0xFF);
        crc = updateByte(crc, (value >>> 16) & 0xFF);
        crc = updateByte(crc, (value >>> 8) & 0xFF);
        crc = updateByte(crc, value & 0xFF);
        return crc & 0xFFFF;
    }

    public static int updateLong(int crc, long value) {
        crc = updateByte(crc, (int) ((value >>> 56) & 0xFF));
        crc = updateByte(crc, (int) ((value >>> 48) & 0xFF));
        crc = updateByte(crc, (int) ((value >>> 40) & 0xFF));
        crc = updateByte(crc, (int) ((value >>> 32) & 0xFF));
        crc = updateByte(crc, (int) ((value >>> 24) & 0xFF));
        crc = updateByte(crc, (int) ((value >>> 16) & 0xFF));
        crc = updateByte(crc, (int) ((value >>> 8) & 0xFF));
        crc = updateByte(crc, (int) (value & 0xFF));
        return crc & 0xFFFF;
    }
}
