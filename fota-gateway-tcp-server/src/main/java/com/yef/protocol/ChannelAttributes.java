package com.yef.protocol;

import io.netty.util.AttributeKey;

public final class ChannelAttributes {

    public static final AttributeKey<String> IMEI = AttributeKey.valueOf("fota.imei");
    public static final AttributeKey<String> SESSION_ID = AttributeKey.valueOf("fota.sessionId");
    public static final AttributeKey<Long> DEVICE_ID = AttributeKey.valueOf("fota.deviceId");
    public static final AttributeKey<Long> CURRENT_TASK_ID = AttributeKey.valueOf("fota.currentTaskId");

    private ChannelAttributes() {
    }
}
