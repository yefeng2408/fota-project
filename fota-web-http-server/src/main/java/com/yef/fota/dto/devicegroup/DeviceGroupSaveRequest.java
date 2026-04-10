package com.yef.fota.dto.devicegroup;

import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceGroupSaveRequest {

    private Long id;

    @NotBlank
    private String deviceGroupName;

    private Long parentId;
}
