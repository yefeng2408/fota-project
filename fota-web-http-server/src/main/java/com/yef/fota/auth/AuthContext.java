package com.yef.fota.auth;

public final class AuthContext {

    private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthUser authUser) {
        HOLDER.set(authUser);
    }

    public static AuthUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        AuthUser authUser = HOLDER.get();
        return authUser == null ? null : authUser.getUserId();
    }

    public static String getUsername() {
        AuthUser authUser = HOLDER.get();
        return authUser == null ? null : authUser.getUsername();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
