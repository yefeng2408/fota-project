package com.yef.fota.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.common.PageResult;
import com.yef.fota.dto.upgrade.UpgradeTaskVO;
import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.entity.OperateLogEntity;
import com.yef.fota.entity.UpgradeTaskEntity;
import com.yef.fota.entity.UserEntity;
import com.yef.fota.service.FirmwarePackageService;
import com.yef.fota.service.OperateLogService;
import com.yef.fota.service.UpgradeTaskService;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.yef.fota.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upgrade-tasks")
public class UpgradeTaskController {

    private final UpgradeTaskService upgradeTaskService;
    private final FirmwarePackageService firmwarePackageService;
    private final UserService userService;

    @GetMapping
    public ApiResponse<PageResult<UpgradeTaskVO>> page(@RequestParam(defaultValue = "1") long current,
                                                       @RequestParam(defaultValue = "10") long pageSize,
                                                       @RequestParam(required = false) Long taskId,
                                                       @RequestParam(required = false) String imei,
                                                       @RequestParam(required = false) String taskStatus) {
        Page<UpgradeTaskEntity> page = upgradeTaskService.lambdaQuery()
                .eq(taskId != null, UpgradeTaskEntity::getTaskId, taskId)
                .like(StringUtils.hasText(imei), UpgradeTaskEntity::getImei, imei)
                .eq(StringUtils.hasText(taskStatus), UpgradeTaskEntity::getTaskStatus, taskStatus)
                .orderByDesc(UpgradeTaskEntity::getCreatedAt)
                .page(new Page<>(current, pageSize));

        List<UpgradeTaskVO> records = toUpgradeTaskVOs(page.getRecords());
        return ApiResponse.ok(new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), records));
    }

    private List<UpgradeTaskVO> toUpgradeTaskVOs(List<UpgradeTaskEntity> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> firmwareIds = tasks.stream()
                .map(UpgradeTaskEntity::getFirmwareId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        java.util.Map<Long, FirmwarePackageEntity> firmwareMap = firmwareIds.isEmpty()
                ? Collections.emptyMap()
                : firmwarePackageService.listByIds(firmwareIds).stream()
                        .collect(Collectors.toMap(FirmwarePackageEntity::getId, Function.identity(), (a, b) -> a));

        return tasks.stream().map(task -> {
            UpgradeTaskVO vo = new UpgradeTaskVO();
            vo.setBatchId(task.getBatchId());
            vo.setTaskId(task.getTaskId());
            vo.setImei(task.getImei());
            vo.setTaskStatus(task.getTaskStatus());
            vo.setProgress(task.getProgress());
            vo.setFailReason(task.getFailReason());
            UserEntity userEntity = userService.getById(task.getOperatorId());
            if(userEntity!=null){
                vo.setOperator(userEntity.getUsername());
            }else {
                vo.setOperator("admin");
            }
            vo.setStartTime(task.getStartTime());
            vo.setEndTime(task.getEndTime());
            vo.setCreatedAt(task.getCreatedAt());

            FirmwarePackageEntity firmware = firmwareMap.get(task.getFirmwareId());
            vo.setFirmwareVersion(firmware == null ? null : firmware.getVersion());
            return vo;
        }).collect(Collectors.toList());
    }
}
