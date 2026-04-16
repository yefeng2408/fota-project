package com.yef.fota.api.dto;

/**
 * @description: 下发0x87 取消升级
 * @author: 叶丰
 * @date: 2026/04/15 22:27
 */
public class GatewayCancelUpgradeRequest {

    private Long taskId;
    private Long deviceId;
    private String imei;

}