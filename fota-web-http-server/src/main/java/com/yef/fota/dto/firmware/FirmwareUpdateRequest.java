package com.yef.fota.dto.firmware;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FirmwareUpdateRequest {

    private Long id;

    @NotBlank
    private String version;

    @NotBlank
    private String deviceType;

    @NotNull
    private Integer chunkSize;

    @NotNull
    private Byte forceUpgrade;

    @NotNull
    private Byte status;

    private String remark;
}
