package com.yef.fota.dto.batch;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class BatchUpgradeStartResponse {
    //batch_upgrade_task表的主键id
    private Long batchTaskId;
    //key:设备imei   value:upgrade_task表的taskId
    private Map<String, Long> taskIds;
    private Long groupId;
    private Long firmwareId;
    private Integer totalCount;
    private Integer startedCount;
    private Integer skippedCount;
    private String summary;

}
