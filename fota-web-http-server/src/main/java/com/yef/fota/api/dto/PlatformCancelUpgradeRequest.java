package com.yef.fota.api.dto;

import lombok.Data;

/**
 * @description: 下发0x87 取消升级
 * @author: 叶丰
 * @date: 2026/04/15 22:27
 */
@Data
public class PlatformCancelUpgradeRequest {

    private String imei;
    private Long taskId;
    private byte reason;

}