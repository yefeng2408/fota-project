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

 Date: 14/04/2026 14:24:07
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for batch_upgrade_task
-- ----------------------------
DROP TABLE IF EXISTS `batch_upgrade_task`;
CREATE TABLE `batch_upgrade_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '批量任务ID',
  `group_id` bigint NOT NULL COMMENT '设备组ID',
  `firmware_id` bigint NOT NULL COMMENT '固件ID',
  `status` varchar(20) COLLATE utf8mb4_bin DEFAULT 'INIT' COMMENT '状态：INIT/RUNNING/FINISHED',
  `total_count` int DEFAULT '0' COMMENT '总设备数',
  `success_count` int DEFAULT '0' COMMENT '成功数量',
  `fail_count` int DEFAULT '0' COMMENT '失败数量',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='批量升级任务表';

SET FOREIGN_KEY_CHECKS = 1;
