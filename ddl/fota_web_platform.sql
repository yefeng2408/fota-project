/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80035 (8.0.35)
 Source Host           : localhost:3306
 Source Schema         : fota_web_platform

 Target Server Type    : MySQL
 Target Server Version : 80035 (8.0.35)
 File Encoding         : 65001

 Date: 07/05/2026 19:07:46
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
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'INIT' COMMENT '状态：INIT/RUNNING/FINISHED',
  `total_count` int DEFAULT '0' COMMENT '总设备数',
  `success_count` int DEFAULT '0' COMMENT '成功数量',
  `fail_count` int DEFAULT '0' COMMENT '失败数量',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `remark` varchar(200) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=114 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='批量升级任务表';

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
) ENGINE=InnoDB AUTO_INCREMENT=72654 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='设备表';

-- ----------------------------
-- Table structure for device_firmware_binding
-- ----------------------------
DROP TABLE IF EXISTS `device_firmware_binding`;
CREATE TABLE `device_firmware_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` bigint NOT NULL COMMENT '设备ID',
  `firmware_id` bigint NOT NULL COMMENT '固件ID',
  `bind_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'BOUND' COMMENT 'BOUND/TRIGGERED/CANCELED/FINISHED',
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
) ENGINE=InnoDB AUTO_INCREMENT=61018 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='设备与固件绑定关系表';

-- ----------------------------
-- Table structure for device_group
-- ----------------------------
DROP TABLE IF EXISTS `device_group`;
CREATE TABLE `device_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '设备组ID',
  `device_group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '设备组名称',
  `parent_id` bigint DEFAULT NULL COMMENT '父节点ID（树结构）',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='设备分组表（树结构）';

-- ----------------------------
-- Table structure for device_group_relation
-- ----------------------------
DROP TABLE IF EXISTS `device_group_relation`;
CREATE TABLE `device_group_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` bigint NOT NULL COMMENT '设备ID',
  `device_group_id` bigint NOT NULL COMMENT '设备组ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_group` (`device_id`,`device_group_id`)
) ENGINE=InnoDB AUTO_INCREMENT=72751 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='设备与设备组关联表';

-- ----------------------------
-- Table structure for device_upgrade_log
-- ----------------------------
DROP TABLE IF EXISTS `device_upgrade_log`;
CREATE TABLE `device_upgrade_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '业务任务ID',
  `device_id` bigint NOT NULL COMMENT '设备ID',
  `packet_no` int DEFAULT NULL COMMENT '分包序号',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '状态：SEND/ACK/FAIL',
  `retry_count` int DEFAULT '0' COMMENT '重试次数',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '日志信息',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_packet` (`task_id`,`packet_no`,`created_at`),
  KEY `idx_device_task` (`device_id`,`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='设备升级过程日志表';

-- ----------------------------
-- Table structure for firmware_package
-- ----------------------------
DROP TABLE IF EXISTS `firmware_package`;
CREATE TABLE `firmware_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID【固件id】',
  `version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '固件版本号，例如 v1.0.1。',
  `device_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '设备类型（例如：D056、D057、MOTOR_V1）',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_url` varchar(500) NOT NULL COMMENT '文件下载地址（OSS/MinIO）',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `chunk_size` int DEFAULT '512' COMMENT '分包大小（字节）。例如：LoRa设备 → 100~500字节、4G设备 → 1KB ~ 4KB、WiFi设备 → 4KB ~ 16KB',
  `total_packet` int DEFAULT '512' COMMENT 'chunk后的总的个数',
  `md5` varchar(64) NOT NULL COMMENT 'MD5值',
  `force_upgrade` tinyint DEFAULT '0' COMMENT '是否强制升级（0否 1是）',
  `status` tinyint DEFAULT '1' COMMENT '状态（0禁用 1启用）',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注说明',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `bucket_name` varchar(100) NOT NULL DEFAULT '' COMMENT 'MinIO桶名称',
  `object_name` varchar(255) NOT NULL DEFAULT '' COMMENT 'MinIO对象名',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_version_device` (`version`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固件包信息表';

-- ----------------------------
-- Table structure for operate_log
-- ----------------------------
DROP TABLE IF EXISTS `operate_log`;
CREATE TABLE `operate_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '操作用户ID',
  `action` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '操作类型（如：START_UPGRADE）',
  `target_id` bigint DEFAULT NULL COMMENT '操作对象ID（如设备ID）',
  `detail` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '操作详情',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1039 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户操作日志表';

-- ----------------------------
-- Table structure for upgrade_task
-- ----------------------------
DROP TABLE IF EXISTS `upgrade_task`;
CREATE TABLE `upgrade_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '任务ID（全局唯一）',
  `device_id` bigint NOT NULL COMMENT '设备ID',
  `imei` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '设备IMEI',
  `firmware_id` bigint NOT NULL COMMENT '固件ID',
  `batch_id` bigint DEFAULT NULL COMMENT '批量任务ID，单任务可为空',
  `task_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'NO_TASK / UPGRADE_REQUESTED / UPGRADING / WAIT_RESULT / SUCCESS / FAIL / TIMEOUT / PAUSED / CANCELING / CANCEL_UPGRADE',
  `progress` int NOT NULL DEFAULT '0' COMMENT '升级进度百分比',
  `current_packet` int NOT NULL DEFAULT '0' COMMENT '最后一次落库的已确认包号快照',
  `total_packet` int NOT NULL DEFAULT '0' COMMENT '总包数',
  `fail_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '失败原因',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人',
  `start_time` datetime DEFAULT NULL COMMENT '开始升级时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `status_version` bigint NOT NULL DEFAULT '0' COMMENT '任务状态版本号',
  `status_event_time` bigint NOT NULL DEFAULT '0' COMMENT '最近一次任务状态事件时间戳(毫秒)',
  `retry_count` int DEFAULT '0' COMMENT '已重试次数',
  `max_retry` int DEFAULT '3' COMMENT '最大重试次数',
  `next_retry_at` datetime DEFAULT NULL COMMENT '下次重试时间',
  `last_error_code` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `last_error_msg` varchar(256) COLLATE utf8mb4_bin DEFAULT NULL,
  `last_active_at` datetime DEFAULT NULL COMMENT '最近活跃时间（心跳/包）',
  `is_delete` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT '0' COMMENT '0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_task_status` (`task_status`),
  KEY `idx_batch_id` (`batch_id`)
) ENGINE=InnoDB AUTO_INCREMENT=61151 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='设备升级任务主表';

-- ----------------------------
-- Table structure for upgrade_task_process
-- ----------------------------
DROP TABLE IF EXISTS `upgrade_task_process`;
CREATE TABLE `upgrade_task_process` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '升级任务ID',
  `device_id` bigint NOT NULL COMMENT '设备ID',
  `event_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '异常/事件类型(PACKET_TIMEOUT/PACKET_CRC16_ERROR/PACKET_SEND_FAIL/PACKET_ACK_MISMATCH)',
  `packet_no` int DEFAULT NULL COMMENT '分包号，任务级异常可为空',
  `retry_no` int NOT NULL DEFAULT '0' COMMENT '当前包重试次数',
  `event_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
  `message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '事件说明',
  `extra_json` json DEFAULT NULL COMMENT '扩展信息，如expectedCrc16/actualCrc16等',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_task_packet` (`task_id`,`packet_no`),
  KEY `idx_task_event` (`task_id`,`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='升级任务异常事件明细表';

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户名',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '手机号（用于登录）',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '密码（加密存储）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户表';

-- ----------------------------
-- Table structure for user_device_group
-- ----------------------------
DROP TABLE IF EXISTS `user_device_group`;
CREATE TABLE `user_device_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `device_group_id` bigint NOT NULL COMMENT '设备组ID',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'VIEWER' COMMENT '角色：OWNER/OPERATOR/VIEWER',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_group` (`user_id`,`device_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户与设备组权限关系表';

SET FOREIGN_KEY_CHECKS = 1;
