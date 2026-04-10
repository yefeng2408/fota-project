package com.yef.fota.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.common.PageResult;
import com.yef.fota.entity.OperateLogEntity;
import com.yef.fota.service.OperateLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/operate-logs")
public class OperateLogController {

    private final OperateLogService operateLogService;

    @GetMapping
    public ApiResponse<PageResult<OperateLogEntity>> page(@RequestParam(defaultValue = "1") long current,
                                                          @RequestParam(defaultValue = "10") long pageSize,
                                                          @RequestParam(required = false) Long userId,
                                                          @RequestParam(required = false) String action) {
        Page<OperateLogEntity> page = operateLogService.lambdaQuery()
                .eq(userId != null, OperateLogEntity::getUserId, userId)
                .like(StringUtils.hasText(action), OperateLogEntity::getAction, action)
                .orderByDesc(OperateLogEntity::getCreatedAt, OperateLogEntity::getId)
                .page(new Page<>(current, pageSize));
        return ApiResponse.ok(PageResult.from(page));
    }
}
