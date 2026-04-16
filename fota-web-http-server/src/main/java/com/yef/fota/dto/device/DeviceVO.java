package com.yef.fota.dto.device;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class DeviceVO {

    private Long id;
    private String imei;
    private String deviceName;
    private String deviceType;
    private String currentFirmwareVersion;
    /**
     * 设备升级状态：
     * 1. 表示当前升级过程状态（如 UPGRADE_REQUESTED / UPGRADING）
     * 2. 也表示最近一轮升级终态（如 SUCCESS / FAIL / TIMEOUT / CANCEL_UPGRADE）
     * 3. 当用户重新编辑设备并重新绑定目标固件时，状态重置为 NO_TASK，进入下一轮升级流程
     */
    private String deviceUpgradeStatus;
    /**
     * 0：未绑定固件  1:已绑定固件
     */
    private Integer isBind;
    /**
     * 是否可升级，N：不可升级  Y：可升级
     */
    private String isUpgradable="N";
    private Long targetFirmwareId;
    private Long lastUpgradeTaskId;
    private Long deviceGroupId;
    private String deviceGroupName;
    /**
     * 目标版本号
     */
    private String targetFirmwareVersion;
    /**
     * 目标固件文件名。
     */
    private String targetFirmwareName;

	 /*   private Long lastUpgradeTaskId;
    private String lastUpgradeTime;
*/
    /**
     * 从redis查询. 0=offline  1=online
     */
    private int isOnline;
    /**
     * 从redis查询
     */
    private LocalDateTime lastOnlineTime;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
