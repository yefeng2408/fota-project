package com.yef.fota.dto.upgrade;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpgradeTaskVO {

    private Long batchId;
    private Long taskId;
    private String imei;
    private String firmwareVersion;
    private String taskStatus;
    private Integer progress;
    private String failReason;
    private String operator;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
}
