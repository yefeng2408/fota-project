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

 Date: 14/04/2026 14:23:07
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for device_firmware_binding
-- ----------------------------
DROP TABLE IF EXISTS `device_firmware_binding`;
CREATE TABLE `device_firmware_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` bigint NOT NULL COMMENT '设备ID',
  `firmware_id` bigint NOT NULL COMMENT '固件ID',
  `bind_status` varchar(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'BOUND' COMMENT 'BOUND/TRIGGERED/CANCELED/FINISHED',
  `triggered` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已触发升级任务',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人',
  `bound_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  `triggered_at` datetime DEFAULT NULL COMMENT '触发升级时间',
  `unbound_at` datetime DEFAULT NULL COMMENT '解绑/失效时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_firmware` (`device_id`,`firmware_id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_firmware_id` (`firmware_id`),
  KEY `idx_bind_status` (`bind_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='设备与固件绑定关系表';

SET FOREIGN_KEY_CHECKS = 1;
