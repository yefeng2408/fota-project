package com.yef.fota.dto.batch;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BatchUpgradeStartRequest {

    @NotNull(message = "设备组不能为空")
    private Long groupId;

    @NotNull(message = "目标固件不能为空")
    private Long firmwareId;
}
