package com.yef.fota.api.dto;

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

    private Long firmwareId;
    private Integer totalPacket;
    private Integer chunkSize;
    private Long fileSize;
    private String md5;
}