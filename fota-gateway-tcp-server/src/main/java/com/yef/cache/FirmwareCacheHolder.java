package com.yef.cache;

import lombok.Data;
import lombok.ToString;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @description: 固件信息本地缓存信息
 * @author: 叶丰
 * @date: 2026/5/6 14:36
 */
@Data
@ToString
public class FirmwareCacheHolder {

    private Long firmwareId;

    private String bucketName;

    private String objectName;

    private byte[] firmwareFullBytes; // 固件直接内存缓存

    private long fileSize;

    private int chunkSize;

    private int totalPacket;

    private String md5;

    private AtomicInteger refCount = new AtomicInteger(0);

    private volatile long createdAt;

    private volatile long lastAccessAt;

}