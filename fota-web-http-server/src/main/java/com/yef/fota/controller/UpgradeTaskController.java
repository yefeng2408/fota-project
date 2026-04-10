package com.yef.fota.controller;

import com.yef.fota.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/upgrade-tasks")
public class UpgradeTaskController {

    @GetMapping("/placeholder")
    public ApiResponse<Map<String, Object>> placeholder() {
        return ApiResponse.ok(Map.of(
                "implemented", false,
                "message", "升级任务管理模块按当前约定先保留占位，后续可在此基础上继续接入"
        ));
    }
}
