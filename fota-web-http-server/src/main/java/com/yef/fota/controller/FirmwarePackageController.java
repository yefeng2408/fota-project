package com.yef.fota.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yef.fota.annotation.OperationLog;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.common.PageResult;
import com.yef.fota.dto.firmware.FirmwarePackageVO;
import com.yef.fota.dto.firmware.FirmwareUpdateRequest;
import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.exception.BusinessException;
import com.yef.fota.service.FirmwarePackageService;
import com.yef.fota.util.FileDigestUtils;
import io.minio.*;
import io.minio.http.Method;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/firmwares")
public class FirmwarePackageController {

    private final FirmwarePackageService firmwarePackageService;

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String minioBucket;

    @GetMapping
    public ApiResponse<PageResult<FirmwarePackageVO>> page(@RequestParam(defaultValue = "1") long current,
                                                           @RequestParam(defaultValue = "10") long pageSize,
                                                           @RequestParam(required = false) String keyword) {
        Page<FirmwarePackageEntity> page = firmwarePackageService.lambdaQuery()
                .and(StringUtils.hasText(keyword), wrapper -> wrapper.like(FirmwarePackageEntity::getVersion, keyword)
                        .or().like(FirmwarePackageEntity::getDeviceType, keyword)
                        .or().like(FirmwarePackageEntity::getFileName, keyword))
                .orderByDesc(FirmwarePackageEntity::getId)
                .page(new Page<>(current, pageSize));
        List<FirmwarePackageVO> records = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return ApiResponse.ok(new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), records));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @OperationLog(action = "UPLOAD_FIRMWARE")
    public ApiResponse<FirmwarePackageEntity> upload(@RequestParam("file") MultipartFile file,
                                                     @RequestParam String version,
                                                     @RequestParam String deviceType,
                                                     @RequestParam Integer chunkSize,
                                                     @RequestParam(defaultValue = "0") Byte forceUpgrade,
                                                     @RequestParam(defaultValue = "1") Byte status,
                                                     @RequestParam(required = false) String remark) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        String objectName = "firmware/" + dateFormat.format(new Date()) + "/" + version + "/" + file.getOriginalFilename();
        FirmwarePackageEntity entity = new FirmwarePackageEntity();
        entity.setVersion(version);
        entity.setDeviceType(deviceType);
        entity.setFileName(file.getOriginalFilename());
        entity.setFileUrl(objectName);
        entity.setFileSize(file.getSize());
        entity.setChunkSize(chunkSize);
        entity.setTotalPacket((int) Math.ceil(file.getSize() * 1.0 / chunkSize));
        entity.setForceUpgrade(forceUpgrade);
        entity.setStatus(status);
        entity.setRemark(remark);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        try (InputStream uploadInputStream = file.getInputStream()) {
            ObjectWriteResponse resp = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(objectName)
                            .stream(uploadInputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            entity.setBucketName(resp.bucket());
            entity.setObjectName(resp.object());
        } catch (Exception e) {
            throw new BusinessException("上传固件到 MinIO 失败: " + e.getMessage());
        }
        try (InputStream md5InputStream = file.getInputStream()) {
            entity.setMd5(FileDigestUtils.md5(md5InputStream));
        }
        firmwarePackageService.save(entity);
        return ApiResponse.ok(entity);
    }

    @PutMapping("/{id}")
    @OperationLog(action = "UPDATE_FIRMWARE")
    public ApiResponse<FirmwarePackageEntity> update(@PathVariable Long id, @RequestBody @Valid FirmwareUpdateRequest request) {
        FirmwarePackageEntity entity = firmwarePackageService.getById(id);
        if (entity == null) {
            throw new BusinessException("固件不存在");
        }
        entity.setVersion(request.getVersion());
        entity.setDeviceType(request.getDeviceType());
        entity.setChunkSize(request.getChunkSize());
        entity.setForceUpgrade(request.getForceUpgrade());
        entity.setStatus(request.getStatus());
        entity.setRemark(request.getRemark());
        entity.setUpdatedAt(LocalDateTime.now());
        firmwarePackageService.updateById(entity);
        return ApiResponse.ok(entity);
    }

    @DeleteMapping("/{id}")
    @OperationLog(action = "DELETE_FIRMWARE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        FirmwarePackageEntity entity = firmwarePackageService.getById(id);
        if (entity != null && StringUtils.hasText(entity.getFileUrl())) {
            try {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(minioBucket)
                                .object(entity.getFileUrl())
                                .build()
                );
            } catch (Exception e) {
                throw new BusinessException("删除 MinIO 固件文件失败: " + e.getMessage());
            }
        }
        firmwarePackageService.removeById(id);
        return ApiResponse.ok(null);
    }

    private FirmwarePackageVO toVO(FirmwarePackageEntity entity) {
        FirmwarePackageVO vo = new FirmwarePackageVO();
        vo.setId(entity.getId());
        vo.setVersion(entity.getVersion());
        vo.setDeviceType(entity.getDeviceType());
        vo.setFileName(entity.getFileName());
        vo.setFileUrl(entity.getFileUrl());
        vo.setDownloadUrl(buildDownloadUrl(entity.getFileUrl()));

        double size = (double)entity.getFileSize() / 1024 / 1024;
        BigDecimal bd = BigDecimal.valueOf(size);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        float result = bd.floatValue();
        vo.setFileSize(result);

        vo.setChunkSize(entity.getChunkSize());
        vo.setTotalPacket(entity.getTotalPacket());
        vo.setMd5(entity.getMd5());
        vo.setForceUpgrade(entity.getForceUpgrade());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private String buildDownloadUrl(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return null;
        }
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioBucket)
                            .object(objectName)
                            .expiry(60 * 60)
                            .build()
            );
        } catch (Exception e) {
            return null;
        }
    }
}
