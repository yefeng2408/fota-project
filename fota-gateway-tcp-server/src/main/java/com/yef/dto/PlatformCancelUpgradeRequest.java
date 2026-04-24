package com.yef.dto;

import lombok.Data;

/**
 * @description: 平台下发取消升级指令
 * @author: 叶丰
 * @date: 2026/04/16 09:57
 */
@Data
public class PlatformCancelUpgradeRequest {

    String imei;
    Long taskId;
    byte reason;

}