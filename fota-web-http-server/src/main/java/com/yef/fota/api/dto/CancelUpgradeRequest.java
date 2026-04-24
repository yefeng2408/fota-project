package com.yef.fota.api.dto;

import lombok.Data;

/**
 * @description: 下发0x87指令请求参数
 * @author: 叶丰
 * @date: 2026/4/16 09:28
 */
@Data
public class CancelUpgradeRequest {

    private String imei;

    private Long taskId;
    //取消原因（0=用户取消。目前就只有用户手动点击取消。因为0x07是用户在平台主动点击下发的取消升级指令）
    private byte reason=(byte)0x00;

}