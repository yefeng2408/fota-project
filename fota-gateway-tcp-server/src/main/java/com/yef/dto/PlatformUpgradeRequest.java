package com.yef.dto;

import lombok.Data;
import lombok.ToString;

/**
 * @description: 网关接收平台下发的0x81的升级请求
 * @author: 叶丰
 * @date: 2026/4/15 22:27
 */
@Data
@ToString
public class PlatformUpgradeRequest {

    private Long taskId;
    private Long deviceId;
    private String imei;
    private byte firmwareNameLen;
    private String firmwareName;
    private byte firmwareVersionLen;
    private String firmwareVersionName;
    private Long firmwareId;
    private Integer chunkSize;
    private Integer totalPacket;
    private Long fileSize;
    private String md5;
    /**
     * MinIO桶名称
     */
    private String bucketName;

    /**
     * MinIO对象名
     */
    private String objectName;
}