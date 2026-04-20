package com.yef.fota.api.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.ToString;

/**
 * @description: 下发0x81 请求升级
 * @author: 叶丰
 * @date: 2026/4/15 22:27
 */
@Data
@ToString
public class GatewayUpgradeRequest {

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