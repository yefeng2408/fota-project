package com.yef.fota.controller;

import com.yef.fota.common.ApiResponse;
import com.yef.fota.dto.dashboard.DashboardOverviewVO;
import com.yef.fota.entity.DeviceUpgradeLogEntity;
import com.yef.fota.entity.UpgradeTaskEntity;
import com.yef.fota.service.DeviceService;
import com.yef.fota.service.DeviceUpgradeLogService;
import com.yef.fota.service.UpgradeTaskService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DeviceService deviceService;
    private final UpgradeTaskService upgradeTaskService;
    private final DeviceUpgradeLogService deviceUpgradeLogService;

    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewVO> overview() {
        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setTotalDevices(deviceService.count());
        vo.setOnlineDevices(0L);
        vo.setSuccessTasks(upgradeTaskService.lambdaQuery().eq(UpgradeTaskEntity::getStatus, "SUCCESS").count());
        vo.setFailedTasks(upgradeTaskService.lambdaQuery().in(UpgradeTaskEntity::getStatus, List.of("FAIL", "TIMEOUT")).count());
        vo.setRecentTasks(upgradeTaskService.lambdaQuery()
                .orderByDesc(UpgradeTaskEntity::getCreatedAt)
                .last("limit 5")
                .list()
                .stream()
                .map(task -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("taskId", task.getTaskId());
                    item.put("imei", task.getImei());
                    item.put("status", task.getStatus());
                    item.put("progress", task.getProgress());
                    item.put("createdAt", task.getCreatedAt());
                    return item;
                }).collect(Collectors.toList()));

        List<DeviceUpgradeLogEntity> logs = deviceUpgradeLogService.lambdaQuery()
                .ge(DeviceUpgradeLogEntity::getCreatedAt, java.time.LocalDateTime.now().minusDays(6).withHour(0).withMinute(0).withSecond(0))
                .list();
        Map<String, Long> grouped = logs.stream().collect(Collectors.groupingBy(
                log -> log.getCreatedAt().toLocalDate().format(DateTimeFormatter.ISO_DATE),
                Collectors.counting()
        ));
        List<Map<String, Object>> trends = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String key = date.format(DateTimeFormatter.ISO_DATE);
            trends.add(Map.of("date", key, "count", grouped.getOrDefault(key, 0L)));
        }
        vo.setLogTrends(trends);
        return ApiResponse.ok(vo);
    }
}
