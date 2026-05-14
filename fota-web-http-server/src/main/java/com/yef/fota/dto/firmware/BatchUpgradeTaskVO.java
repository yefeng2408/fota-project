package com.yef.fota.dto.firmware;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * <p>
 * 批量升级任务
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Getter
@Setter
public class BatchUpgradeTaskVO {


    /**
     * 批量任务ID
     */
    private Long id;

    /**
     * 设备组名称
     */
    private String groupName;

    /**
     * 固件名称及版本号
     */
    private String firmware;

    /**
     * 状态：INIT/RUNNING/FINISHED
     */
    private String status;

    /**
     * 总设备数
     */
    private Integer totalCount;

    /**
     * 成功数量
     */
    private Integer successCount;

    /**
     * 失败数量
     */
    private Integer failCount;

 /*   *//**
     * 创建人
     *//*
    @TableField("created_by")
    private Long createdBy;*/

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;
}
