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
 * 固件包信息表
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Getter
@Setter
@TableName("firmware_package")
public class FirmwarePackageEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 版本号，例如 v1.0.3。假设设备出厂默认固件版本号是 v1.0.0
     */
    @TableField("version")
    private String version;

    /**
     * 设备类型（可选，不同设备不同固件,如：电动车设备、校车、大客车等）
     */
    @TableField("device_type")
    private String deviceType;

    /**
     * 文件名
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 文件下载地址（OSS/MinIO）
     */
    @TableField("file_url")
    private String fileUrl;

    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 分包大小（字节）。例如：LoRa设备 → 100~500字节、4G设备 → 1KB ~ 4KB、WiFi设备 → 4KB ~ 16KB
     */
    @TableField("chunk_size")
    private Integer chunkSize;

    /**
     * chunk后的总的个数
     */
    @TableField("total_packet")
    private Integer totalPacket;

    /**
     * MD5值
     */
    @TableField("md5")
    private String md5;

    /**
     * 是否强制升级（0否 1是）
     */
    @TableField("force_upgrade")
    private Byte forceUpgrade;

    /**
     * 状态（0禁用 1启用）
     */
    @TableField("status")
    private Byte status;

    /**
     * 备注说明
     */
    @TableField("remark")
    private String remark;

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

    /**
     * MinIO桶名称
     */
    @TableField("bucket_name")
    private String bucketName;

    /**
     * MinIO对象名
     */
    @TableField("object_name")
    private String objectName;

}
