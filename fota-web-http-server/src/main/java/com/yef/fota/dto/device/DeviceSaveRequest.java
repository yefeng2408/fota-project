package com.yef.fota.dto.device;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceSaveRequest {

    private Long id;

    @NotBlank
    private String imei;

    @NotBlank
    private String deviceName;

    @NotBlank
    private String deviceType;

    private String currentFirmwareVersion;

    private String deviceUpgradeStatus;

    private Long targetFirmwareId;

    @NotNull
    private Long deviceGroupId;
}
