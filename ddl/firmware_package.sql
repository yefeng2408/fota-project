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

 Date: 14/04/2026 14:23:39
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
  `chunk_count` int DEFAULT '512' COMMENT 'chunk后的总的个数',
  `md5` varchar(64) NOT NULL COMMENT 'MD5值',
  `force_upgrade` tinyint DEFAULT '0' COMMENT '是否强制升级（0否 1是）',
  `status` tinyint DEFAULT '1' COMMENT '状态（0禁用 1启用）',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注说明',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_version_device` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固件包信息表';

SET FOREIGN_KEY_CHECKS = 1;
