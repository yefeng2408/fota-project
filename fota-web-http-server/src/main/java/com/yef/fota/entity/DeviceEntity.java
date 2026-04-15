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
     * 当前绑定的目标固件ID
     */
    @TableField("target_firmware_id")
    private Long targetFirmwareId;

    /**
     *  设备当前升级状态
     *  IDLE：平台首次新增设备，还未绑定固件
     *  BOUND：已绑定固件
     *  READY：已绑定固件，且处于在线状态
     *  UPGRADE_REQUESTED： 平台下发0x81升级请求指令
     *  UPGRADING：升级中。平台下发开始0x82指令，且收到设备对于该指令的ACK
     *  SUCCESS/FAIL：升级成功/失败。平台收到设备主动上行的0x06指令。平台收到0x06指令后，就不会在继续下发消息给设备
     *
     *  TIMEOUT：TODO：TIMEOUT 表示升级任务在 UPGRADE_REQUESTED 或 UPGRADING 阶段，超过配置时间(150s)没有收到任何升级链路有效交互（不含普通心跳），由平台异步任务统一判定为超时。
     *
     *  PAUSED：TODO：如果设备突然掉线 就更新为PAUSED，升级暂停状态只会出现在upgrading过程中且设备断线的时候，才会产生这个状态。
     *                如何判断设备掉线？在UPGRADING环节中，平台在多轮的下发0x82指令环节中，设备网关判定ctx.channel().isActive()是否存活
     *                总结：表示“升级过程中发生中断，但任务仍可恢复”，例如：
     * 	                                                        •	设备掉线
     * 	                                                        •	网关重启
     * 	                                                        •	网络临时中断
     * 	                                                        它不是最终失败态，而是“暂挂态”。
     *
     *  CANCEL_UPGRADE：TODO：取消升级。设备还正处于升级环节过程中（已经下发UPGRADE_REQUESTED，但还未SUCCESS。）
     *                   平台用户主动点击“取消升级”(下行0x87指令)，设备网关收到mock-device-client该上行指令，设备网关给平台回复messageType = 0x03 ackType =4的应答ACK。
     *                   然后网关主动断开可以设备的连接！！！。
     *              更合理的定义是：(by gpt)
     *              CANCEL_UPGRADE 表示平台主动终止本轮升级流程。平台向设备下发取消升级指令（如 0x87），设备应答取消 ACK 后，当前升级任务结束，不再继续发送任何升级相关分包。
     *              为什么不建议主动断连
     *              因为断连会影响：
     * 	                •	普通心跳
     * 	                •	设备其他业务上报
     * 	                •	设备在线状态
     *
     *
     * 	         建议允许发起新升级的状态:
     * 	            IDLE
     *              READY
     *              SUCCESS
     *              FAIL
     *              TIMEOUT
     *              CANCEL_UPGRADE
     *          为什么要加这些
     *          FAIL
     *          失败后通常是允许重新发起升级的，不然设备永远锁死。
     *
     *          TIMEOUT
     *          超时后大概率也允许重新发起。
     *
     *          READY
     *          如果设备已经绑定固件但还没开始，你应该允许重新绑定新版本，或者替换待升级版本。
     *
     */
    @TableField("device_upgrade_status")
    private String deviceUpgradeStatus;

    /**
     *  设备是否绑定了固件 1:已绑定 或 0：未绑定
     */
    @TableField("is_bind")
    private Integer isBind;

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
