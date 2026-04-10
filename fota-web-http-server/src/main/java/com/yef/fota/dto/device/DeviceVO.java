package com.yef.fota.dto.device;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceVO {

    private Long id;
    private String imei;
    private String deviceName;
    private String firmwareVersion;
    private Long deviceGroupId;
    private String deviceGroupName;
    private String onlineStatus;
    private LocalDateTime lastOnlineTime;
    private LocalDateTime createdAt;
}
