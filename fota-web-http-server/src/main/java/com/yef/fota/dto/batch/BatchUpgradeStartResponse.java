package com.yef.fota.dto.batch;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BatchUpgradeStartResponse {

    private Long batchTaskId;
    private Long groupId;
    private Long firmwareId;
    private Integer totalCount;
    private Integer startedCount;
    private Integer skippedCount;
    private String summary;
}
