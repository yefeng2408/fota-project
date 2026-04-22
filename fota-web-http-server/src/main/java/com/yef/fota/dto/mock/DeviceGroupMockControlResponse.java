package com.yef.fota.dto.mock;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceGroupMockControlResponse {

    private Long groupId;
    private Integer totalCount;
    private Integer successCount;
    private Integer skippedCount;
    private String summary;
}
