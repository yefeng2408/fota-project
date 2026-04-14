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
    private String deviceType;
    private String currentFirmwareVersion;
    private String deviceUpgradeStatus;
    private Long targetFirmwareId;
    private Long lastUpgradeTaskId;
    private Long deviceGroupId;
    private String deviceGroupName;
    /**
     * 绑定的固件版本号。也就是本次需呀哦升级的固件
     */
    private String targetFirmwareVersion;

 /*   private Long lastUpgradeTaskId;
    private String lastUpgradeTime;
*/
    /**
     * 从redis查询
     */
    private String onlineStatus;
    /**
     * 从redis查询
     */
    private LocalDateTime lastOnlineTime;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
