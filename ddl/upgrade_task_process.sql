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

 Date: 14/04/2026 14:25:52
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for upgrade_task_process
-- ----------------------------
DROP TABLE IF EXISTS `upgrade_task_process`;
CREATE TABLE `upgrade_task_process` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '升级任务ID',
  `device_id` bigint NOT NULL COMMENT '设备ID',
  `event_type` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '异常/事件类型(PACKET_TIMEOUT/PACKET_CRC16_ERROR/PACKET_SEND_FAIL/PACKET_ACK_MISMATCH)',
  `packet_no` int DEFAULT NULL COMMENT '分包号，任务级异常可为空',
  `retry_no` int NOT NULL DEFAULT '0' COMMENT '当前包重试次数',
  `event_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
  `message` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '事件说明',
  `extra_json` json DEFAULT NULL COMMENT '扩展信息，如expectedCrc16/actualCrc16等',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_task_packet` (`task_id`,`packet_no`),
  KEY `idx_task_event` (`task_id`,`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='升级任务异常事件明细表';

SET FOREIGN_KEY_CHECKS = 1;
