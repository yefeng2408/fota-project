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
     *   NO_TASK: '未升级',
     *   UPGRADE_REQUESTED: '已下发升级请求',
     *   UPGRADING: '升级中',
     *   SUCCESS: '升级成功',
     *   FAIL: '升级失败',
     *   TIMEOUT: '升级超时',
     *   PAUSED: '升级暂停',
     *   CANCEL_UPGRADE: '用户取消升级'
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
     * 升级进度
     */
    private int process;
    /**
     * 从redis查询
     */
    private LocalDateTime lastOnlineTime;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
