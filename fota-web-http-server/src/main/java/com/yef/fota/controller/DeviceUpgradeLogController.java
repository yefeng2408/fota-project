package com.yef.fota.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.common.PageResult;
import com.yef.fota.entity.DeviceUpgradeLogEntity;
import com.yef.fota.service.DeviceUpgradeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upgrade-logs")
public class DeviceUpgradeLogController {

    private final DeviceUpgradeLogService deviceUpgradeLogService;

    @GetMapping
    public ApiResponse<PageResult<DeviceUpgradeLogEntity>> page(@RequestParam(defaultValue = "1") long current,
                                                                @RequestParam(defaultValue = "10") long pageSize,
                                                                @RequestParam(required = false) String taskId,
                                                                @RequestParam(required = false) Long deviceId,
                                                                @RequestParam(required = false) String status) {
        Page<DeviceUpgradeLogEntity> page = deviceUpgradeLogService.lambdaQuery()
                .eq(StringUtils.hasText(taskId), DeviceUpgradeLogEntity::getTaskId, taskId)
                .eq(deviceId != null, DeviceUpgradeLogEntity::getDeviceId, deviceId)
                .eq(StringUtils.hasText(status), DeviceUpgradeLogEntity::getStatus, status)
                .orderByDesc(DeviceUpgradeLogEntity::getCreatedAt, DeviceUpgradeLogEntity::getId)
                .page(new Page<>(current, pageSize));
        return ApiResponse.ok(PageResult.from(page));
    }
}
