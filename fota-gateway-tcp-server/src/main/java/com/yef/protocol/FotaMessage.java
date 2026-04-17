package com.yef.protocol;

public interface FotaMessage {

    byte getMessageType();

    String imei();

    Long getTaskId();
}
