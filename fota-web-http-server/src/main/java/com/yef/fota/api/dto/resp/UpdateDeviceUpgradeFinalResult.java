package com.yef.fota.api.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @description: 
 * @author: 叶丰
 * @date: 2026/4/21 10:52
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateDeviceUpgradeFinalResult implements Serializable {

    private String imei;
    private String taskId;
    private String taskStatus;
    private Integer progress;
    private String currentFirmwareVersion;
    private String targetFirmwareVersion;
    private int currentPacket;
    private int totalPacket;
    private String failReason;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

}