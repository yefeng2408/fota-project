package com.yef.fota.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.common.PageResult;
import com.yef.fota.entity.DeviceFirmwareBindingEntity;
import com.yef.fota.service.DeviceFirmwareBindingService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/device-firmware-bindings")
public class DeviceFirmwareBindingController {

    private final DeviceFirmwareBindingService deviceFirmwareBindingService;

    @GetMapping
    public ApiResponse<PageResult<DeviceFirmwareBindingEntity>> page(@RequestParam(defaultValue = "1") long current,
                                                                     @RequestParam(defaultValue = "10") long pageSize,
                                                                     @RequestParam(required = false) Long deviceId,
                                                                     @RequestParam(required = false) Long firmwareId,
                                                                     @RequestParam(required = false) String bindStatus) {
        Page<DeviceFirmwareBindingEntity> page = deviceFirmwareBindingService.lambdaQuery()
                .eq(deviceId != null, DeviceFirmwareBindingEntity::getDeviceId, deviceId)
                .eq(firmwareId != null, DeviceFirmwareBindingEntity::getFirmwareId, firmwareId)
                .eq(StringUtils.hasText(bindStatus), DeviceFirmwareBindingEntity::getBindStatus, bindStatus)
                .orderByDesc(DeviceFirmwareBindingEntity::getId)
                .page(new Page<>(current, pageSize));
        return ApiResponse.ok(PageResult.from(page));
    }
}
