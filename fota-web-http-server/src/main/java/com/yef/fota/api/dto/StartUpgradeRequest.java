package com.yef.fota.api.dto;

import lombok.Data;

/**
 * @description: 下发0x81指令请求参数
 * @author: 叶丰
 * @date: 2026/4/16 09:28
 */
@Data
public class StartUpgradeRequest {

    private String imei;
}