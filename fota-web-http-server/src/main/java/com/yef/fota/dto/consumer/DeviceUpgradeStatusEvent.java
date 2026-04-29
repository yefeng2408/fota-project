/*
package com.yef.fota.dto.consumer;

import lombok.Data;

*/
/**
 * @description: TODO
 * @author: yefeng
 * @date: 2026/04/15 01:19
 *//*

@Data
public class DeviceUpgradeStatusEvent {

    */
/**
     * 设备imei
     *//*

    private String imei;

    */
/**
     * 升级任务ID
     *//*

    private Long taskId;

    */
/**
     * 设备升级状态
     * NO_TASK / UPGRADE_REQUESTED / UPGRADING / SUCCESS / FAIL / TIMEOUT / PAUSED / CANCEL
     *//*

    private String upgradeStatus;

    */
/**
     * 状态版本号：单设备维度单调递增
     *      NO_TASK -> UPGRADE_REQUESTED   version=1
     *      UPGRADE_REQUESTED -> UPGRADING version=2
     *      UPGRADING -> SUCCESS           version=3
     *//*

    private Long version;

    */
/**
     * 状态事件发生时间（毫秒）
     *//*

    private Long eventTime;

    */
/**
     * 可选：当前进度（0-100）
     * 高频 ACK 不建议全量发 MQ，但可以给关键状态事件带上快照
     *//*

    private Integer progress;

    */
/**
     * 可选：分包号快照
     *//*

    private Integer packetNo;



}*/
