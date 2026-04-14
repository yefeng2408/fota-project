package com.yef.fota.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.common.PageResult;
import com.yef.fota.entity.BatchUpgradeTaskEntity;
import com.yef.fota.service.BatchUpgradeTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/batch-upgrade-tasks")
public class BatchUpgradeTaskController {

    private final BatchUpgradeTaskService batchUpgradeTaskService;

    @GetMapping
    public ApiResponse<PageResult<BatchUpgradeTaskEntity>> page(@RequestParam(defaultValue = "1") long current,
                                                                @RequestParam(defaultValue = "10") long pageSize,
                                                                @RequestParam(required = false) Long groupId,
                                                                @RequestParam(required = false) Long firmwareId,
                                                                @RequestParam(required = false) String status) {
        Page<BatchUpgradeTaskEntity> page = batchUpgradeTaskService.lambdaQuery()
                .eq(groupId != null, BatchUpgradeTaskEntity::getGroupId, groupId)
                .eq(firmwareId != null, BatchUpgradeTaskEntity::getFirmwareId, firmwareId)
                .eq(StringUtils.hasText(status), BatchUpgradeTaskEntity::getStatus, status)
                .orderByDesc(BatchUpgradeTaskEntity::getId)
                .page(new Page<>(current, pageSize));
        return ApiResponse.ok(PageResult.from(page));
    }
}
