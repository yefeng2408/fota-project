package com.yef.util;

public final class Crc16Utils {

    private static final int POLYNOMIAL = 0xA001;

    private Crc16Utils() {
    }

    public static int calculate(byte[] data) {
        int crc = 0xFFFF;
        if (data == null) {
            return crc;
        }
        for (byte b : data) {
            crc ^= b & 0xFF;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ POLYNOMIAL;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }
}
