package com.yef.fota.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.CRC32;

public final class FileDigestUtils {

    private FileDigestUtils() {
    }

    public static String md5(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IOException("Failed to calculate md5", ex);
        }
    }

    public static String crc32(byte[] content) {
        CRC32 crc32 = new CRC32();
        crc32.update(content);
        return Long.toHexString(crc32.getValue()).toUpperCase();
    }
}
