package com.yef;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备升级状态相关事件消息。
 *
 * gateway 负责生产事件，web-http-server 负责消费事件并更新 DB / WebSocket。
 */
public class UpgradeEventMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 全局唯一事件 ID，用于消费端幂等。
     */
    private String eventId;

    /**
     * 事件类型：PROGRESS / FINAL_RESULT / START_TIME / CANCEL_RESULT / UPGRADING。
     */
    private String eventType;

    /**
     * 设备 IMEI。
     */
    private String imei;

    /**
     * 升级任务 ID，全局唯一。
     */
    private Long taskId;

    /**
     * 升级状态。
     */
    private String upgradeStatus;

    /**
     * 状态版本号：单设备维度单调递增。
     */
    private Long version;

    /**
     * 升级进度百分比，0-100。
     */
    private Integer progress;

    /**
     * 升级开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 升级结束时间。
     */
    private LocalDateTime endTime;

    /**
     * 当前确认/处理到的分包号。
     */
    private Integer currentPacket;

    /**
     * 总分包数。
     */
    private Integer totalPacket;

    /**
     * 当前固件版本。
     */
    private String currentFirmwareVersion;

    /**
     * 目标固件版本。
     */
    private String targetFirmwareVersion;

    /**
     * 失败错误码，成功时可为空。
     */
    private String errorCode;

    /**
     * 失败原因，成功时可为空。
     */
    private String failReason;

    /**
     * 事件发生时间戳，毫秒。
     */
    private Long eventTime;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getUpgradeStatus() {
        return upgradeStatus;
    }

    public void setUpgradeStatus(String upgradeStatus) {
        this.upgradeStatus = upgradeStatus;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getCurrentPacket() {
        return currentPacket;
    }

    public void setCurrentPacket(Integer currentPacket) {
        this.currentPacket = currentPacket;
    }

    public Integer getTotalPacket() {
        return totalPacket;
    }

    public void setTotalPacket(Integer totalPacket) {
        this.totalPacket = totalPacket;
    }

    public String getCurrentFirmwareVersion() {
        return currentFirmwareVersion;
    }

    public void setCurrentFirmwareVersion(String currentFirmwareVersion) {
        this.currentFirmwareVersion = currentFirmwareVersion;
    }

    public String getTargetFirmwareVersion() {
        return targetFirmwareVersion;
    }

    public void setTargetFirmwareVersion(String targetFirmwareVersion) {
        this.targetFirmwareVersion = targetFirmwareVersion;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Long getEventTime() {
        return eventTime;
    }

    public void setEventTime(Long eventTime) {
        this.eventTime = eventTime;
    }

    public static final class EventType {
        public static final String START_TIME = "START_TIME";
        public static final String UPGRADING = "UPGRADING";
        public static final String PROGRESS = "PROGRESS";
        public static final String FINAL_RESULT = "FINAL_RESULT";
        public static final String CANCEL_RESULT = "CANCEL_RESULT";

        private EventType() {
        }
    }
}
