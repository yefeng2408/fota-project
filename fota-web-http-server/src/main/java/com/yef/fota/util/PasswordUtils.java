package com.yef.fota.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class PasswordUtils {

    private PasswordUtils() {
    }

    public static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Password encode error", ex);
        }
    }
}
