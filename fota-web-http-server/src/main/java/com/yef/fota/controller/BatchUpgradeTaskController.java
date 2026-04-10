package com.yef.fota.controller;

import com.yef.fota.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batch-upgrade-tasks")
public class BatchUpgradeTaskController {

    @GetMapping("/placeholder")
    public ApiResponse<Map<String, Object>> placeholder() {
        return ApiResponse.ok(Map.of(
                "implemented", false,
                "message", "批量升级任务模块按当前约定先保留占位，后续接入你的升级设计"
        ));
    }
}
