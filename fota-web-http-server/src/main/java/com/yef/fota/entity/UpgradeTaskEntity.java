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
 * 设备升级任务表
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Getter
@Setter
@TableName("upgrade_task")
public class UpgradeTaskEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据库主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID(全局唯一)
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 设备ID
     */
    @TableField("device_id")
    private Long deviceId;

    /**
     * 设备IMEI
     */
    @TableField("imei")
    private String imei;

    /**
     * 固件ID
     */
    @TableField("firmware_id")
    private Long firmwareId;

    /**
     * 批量任务ID（为空表示单独升级）
     */
    @TableField("batch_id")
    private Long batchId;

    /**
     * 任务状态：INIT/PENDING/UPGRADE_REQUESTED/UPGRADING/SUCCESS/FAIL/TIMEOUT/CANCELLED
     */
    @TableField("task_status")
    private String taskStatus;

    /**
     * 升级进度（百分比）
     */
    @TableField("progress")
    private Integer progress;

    /**
     * 当前发送包序号
     */
    @TableField("current_packet")
    private Integer currentPacket;

    /**
     * 总包数
     */
    @TableField("total_packet")
    private Integer totalPacket;

    /**
     * 失败原因
     */
    @TableField("fail_reason")
    private String failReason;

    /**
     * 操作人
     */
    @TableField("operator_id")
    private Long operatorId;

    /**
     * 升级开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 升级结束时间
     */
    @TableField("end_time")
    private LocalDateTime endTime;

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
