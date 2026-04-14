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
 * 设备与固件绑定关系表
 * </p>
 */
@Getter
@Setter
@TableName("device_firmware_binding")
public class DeviceFirmwareBindingEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("device_id")
    private Long deviceId;

    @TableField("firmware_id")
    private Long firmwareId;

    @TableField("bind_status")
    private String bindStatus;

    @TableField("triggered")
    private Boolean triggered;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("bound_at")
    private LocalDateTime boundAt;

    @TableField("triggered_at")
    private LocalDateTime triggeredAt;

    @TableField("unbound_at")
    private LocalDateTime unboundAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
