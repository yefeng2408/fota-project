package com.yef.fota.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 设备表
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Getter
@Setter
@TableName("device")
public class DeviceEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 设备IMEI
     */
    @TableField("imei")
    private String imei;

    /**
     * 设备名称
     */
    @TableField("device_name")
    private String deviceName;

    /**
     * 设备类型
     */
    @TableField("device_type")
    private String deviceType;

    /**
     * 当前固件版本
     */
    @TableField("current_firmware_version")
    private String currentFirmwareVersion;

    /**
     * 设备当前升级状态
     */
    @TableField("device_upgrade_status")
    private String deviceUpgradeStatus;

    /**
     * 当前绑定的目标固件ID
     */
    @TableField("target_firmware_id")
    private Long targetFirmwareId;

    /**
     * 最近一次升级任务ID
     */
    @TableField("last_upgrade_task_id")
    private Long lastUpgradeTaskId;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
