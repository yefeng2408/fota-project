package com.yef.fota.dto.firmware;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FirmwarePackageVO {

    private Long id;
    private String version;
    private String deviceType;
    private String fileName;
    /**
     * MinIO objectName，不是浏览器可直接访问 URL。
     */
    private String fileUrl;
    /**
     * MinIO 临时下载地址。
     */
    private String downloadUrl;
    private Long fileSize;
    private Integer chunkSize;
    private Integer totalPacket;
    private String md5;
    private Byte forceUpgrade;
    private Byte status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
