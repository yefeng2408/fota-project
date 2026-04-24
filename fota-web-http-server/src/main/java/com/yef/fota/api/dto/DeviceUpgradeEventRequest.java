package com.yef.fota.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description: 
 * @author: 叶丰
 * @date: 2026/4/21 10:52
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceUpgradeEventRequest {
    private String imei;
    private String status;
    private Integer progress;
    private String currentFirmwareVersion;
    private String targetFirmwareVersion;
}