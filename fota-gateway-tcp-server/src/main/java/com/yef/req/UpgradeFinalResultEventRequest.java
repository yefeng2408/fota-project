package com.yef.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * @description:
 * @author: 叶丰
 * @date: 2026/4/21 10:52
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpgradeFinalResultEventRequest {

    private String imei;
    private String taskId;
    private String upgradeStatus;
    private Integer progress;
    private String currentFirmwareVersion;
    private String targetFirmwareVersion;
    private int currentPacket;
    private int totalPacket;
    //失败原因。更新至upgrade_task表的fail_reason字段
    private String failReason;
    //升级结束时间。更新至upgrade_task表的end_time字段
    private LocalDateTime endTime;

}