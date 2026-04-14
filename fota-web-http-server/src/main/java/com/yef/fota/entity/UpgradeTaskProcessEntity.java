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
 * 升级任务异常事件明细表
 * </p>
 */
@Getter
@Setter
@TableName("upgrade_task_process")
public class UpgradeTaskProcessEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("device_id")
    private Long deviceId;

    @TableField("event_type")
    private String eventType;

    @TableField("packet_no")
    private Integer packetNo;

    @TableField("retry_no")
    private Integer retryNo;

    @TableField("event_time")
    private LocalDateTime eventTime;

    @TableField("message")
    private String message;

    @TableField("extra_json")
    private String extraJson;
}
