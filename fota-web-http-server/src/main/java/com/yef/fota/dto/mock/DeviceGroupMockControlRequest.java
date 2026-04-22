package com.yef.fota.dto.mock;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceGroupMockControlRequest {

    @NotNull(message = "groupId不能为空")
    private Long groupId;
}
