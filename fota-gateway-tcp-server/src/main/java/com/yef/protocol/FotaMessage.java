package com.yef.protocol;

public interface FotaMessage {

    byte getMessageType();

    String getImei();

    Long getTaskId();
}
