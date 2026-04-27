package com.yef.fota.dto.batch;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

@Getter
@Setter
public class BatchUpgradeStartRequest {

    @NotNull(message = "设备组不能为空")
    private Long groupId;

    @NotNull(message = "目标固件不能为空")
    private Long firmwareId;

    @NotEmpty(message = "备注不能为空")
    @Length(max = 300)
    private String remark;
}
