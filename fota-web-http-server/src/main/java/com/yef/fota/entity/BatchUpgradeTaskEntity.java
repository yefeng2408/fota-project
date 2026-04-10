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
 * 批量升级任务表
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Getter
@Setter
@TableName("batch_upgrade_task")
public class BatchUpgradeTaskEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 批量任务ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 设备组ID
     */
    @TableField("group_id")
    private Long groupId;

    /**
     * 固件ID
     */
    @TableField("firmware_id")
    private Long firmwareId;

    /**
     * 状态：INIT/RUNNING/FINISHED
     */
    @TableField("status")
    private String status;

    /**
     * 总设备数
     */
    @TableField("total_count")
    private Integer totalCount;

    /**
     * 成功数量
     */
    @TableField("success_count")
    private Integer successCount;

    /**
     * 失败数量
     */
    @TableField("fail_count")
    private Integer failCount;

    /**
     * 创建人
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
