package com.yef.fota.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yef.fota.annotation.OperationLog;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.common.PageResult;
import com.yef.fota.dto.firmware.FirmwareUpdateRequest;
import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.exception.BusinessException;
import com.yef.fota.service.FirmwarePackageService;
import com.yef.fota.util.FileDigestUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;
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

    @Value("${fota.file-storage.path}")
    private String fileStoragePath;

    @GetMapping
    public ApiResponse<PageResult<FirmwarePackageEntity>> page(@RequestParam(defaultValue = "1") long current,
                                                               @RequestParam(defaultValue = "10") long pageSize,
                                                               @RequestParam(required = false) String keyword) {
        Page<FirmwarePackageEntity> page = firmwarePackageService.lambdaQuery()
                .and(StringUtils.hasText(keyword), wrapper -> wrapper.like(FirmwarePackageEntity::getVersion, keyword)
                        .or().like(FirmwarePackageEntity::getDeviceType, keyword)
                        .or().like(FirmwarePackageEntity::getFileName, keyword))
                .orderByDesc(FirmwarePackageEntity::getId)
                .page(new Page<>(current, pageSize));
        return ApiResponse.ok(PageResult.from(page));
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
        Path uploadDir = Paths.get(fileStoragePath).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
        String suffix = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + (StringUtils.hasText(suffix) ? "." + suffix : "");
        Path targetPath = uploadDir.resolve(storedName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        FirmwarePackageEntity entity = new FirmwarePackageEntity();
        entity.setVersion(version);
        entity.setDeviceType(deviceType);
        entity.setFileName(file.getOriginalFilename());
        entity.setFileUrl(targetPath.toString());
        entity.setFileSize(file.getSize());
        entity.setChunkSize(chunkSize);
        entity.setForceUpgrade(forceUpgrade);
        entity.setStatus(status);
        entity.setRemark(remark);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setMd5(FileDigestUtils.md5(Files.newInputStream(targetPath)));
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
                Files.deleteIfExists(Paths.get(entity.getFileUrl()));
            } catch (IOException ignored) {
            }
        }
        firmwarePackageService.removeById(id);
        return ApiResponse.ok(null);
    }
}
