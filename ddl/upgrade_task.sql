/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80035 (8.0.35)
 Source Host           : localhost:3306
 Source Schema         : fota

 Target Server Type    : MySQL
 Target Server Version : 80035 (8.0.35)
 File Encoding         : 65001

 Date: 15/04/2026 01:16:54
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for upgrade_task
-- ----------------------------
DROP TABLE IF EXISTS `upgrade_task`;
CREATE TABLE `upgrade_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '任务ID（全局唯一）',
  `device_id` bigint NOT NULL COMMENT '设备ID',
  `imei` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '设备IMEI',
  `firmware_id` bigint NOT NULL COMMENT '固件ID',
  `batch_id` bigint DEFAULT NULL COMMENT '批量任务ID，单任务可为空',
  `task_status` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT 'INIT/PENDING/UPGRADE_REQUESTED/UPGRADING/SUCCESS/FAIL/TIMEOUT/CANCELLED',
  `progress` int NOT NULL DEFAULT '0' COMMENT '升级进度百分比',
  `current_packet` int NOT NULL DEFAULT '0' COMMENT '最后一次落库的已确认包号快照',
  `total_packet` int NOT NULL DEFAULT '0' COMMENT '总包数',
  `fail_reason` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '失败原因',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人',
  `start_time` datetime DEFAULT NULL COMMENT '开始升级时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `status_version` bigint NOT NULL DEFAULT '0' COMMENT '任务状态版本号',
  `status_event_time` bigint NOT NULL DEFAULT '0' COMMENT '最近一次任务状态事件时间戳(毫秒)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_task_status` (`task_status`),
  KEY `idx_batch_id` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='设备升级任务主表';

SET FOREIGN_KEY_CHECKS = 1;
