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

 Date: 15/04/2026 01:17:08
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for device
-- ----------------------------
DROP TABLE IF EXISTS `device`;
CREATE TABLE `device` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `imei` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '设备IMEI',
  `device_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '设备名称',
  `device_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '设备类型【D056、D057、MOTOR_V1】',
  `current_firmware_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当前固件版本',
  `device_upgrade_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'IDLE' COMMENT '设备当前升级状态',
  `is_bind` tinyint DEFAULT '0' COMMENT '当前绑定的目标固件ID',
  `target_firmware_id` bigint DEFAULT NULL COMMENT '当前绑定的目标固件ID',
  `last_upgrade_task_id` bigint DEFAULT NULL COMMENT '最近一次升级任务ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `status_version` smallint NOT NULL DEFAULT '0' COMMENT '设备升级状态版本号',
  `status_event_time` bigint NOT NULL DEFAULT '0' COMMENT '最近一次状态事件时间戳(毫秒)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_imei` (`imei`),
  KEY `idx_device_type` (`device_type`),
  KEY `idx_upgrade_status` (`device_upgrade_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='设备表';

SET FOREIGN_KEY_CHECKS = 1;
