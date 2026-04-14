package com.yef.fota.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.common.PageResult;
import com.yef.fota.entity.UpgradeTaskProcessEntity;
import com.yef.fota.service.UpgradeTaskProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upgrade-task-processes")
public class UpgradeTaskProcessController {

    private final UpgradeTaskProcessService upgradeTaskProcessService;

    @GetMapping
    public ApiResponse<PageResult<UpgradeTaskProcessEntity>> page(@RequestParam(defaultValue = "1") long current,
                                                                  @RequestParam(defaultValue = "10") long pageSize,
                                                                  @RequestParam(required = false) Long taskId,
                                                                  @RequestParam(required = false) Long deviceId,
                                                                  @RequestParam(required = false) String eventType) {
        Page<UpgradeTaskProcessEntity> page = upgradeTaskProcessService.lambdaQuery()
                .eq(taskId != null, UpgradeTaskProcessEntity::getTaskId, taskId)
                .eq(deviceId != null, UpgradeTaskProcessEntity::getDeviceId, deviceId)
                .eq(StringUtils.hasText(eventType), UpgradeTaskProcessEntity::getEventType, eventType)
                .orderByDesc(UpgradeTaskProcessEntity::getId)
                .page(new Page<>(current, pageSize));
        return ApiResponse.ok(PageResult.from(page));
    }
}
