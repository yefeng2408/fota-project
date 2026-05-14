package com.yef.fota.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yef.fota.annotation.OperationLog;
import com.yef.fota.auth.AuthContext;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.common.PageResult;
import com.yef.fota.dto.batch.BatchUpgradeStartRequest;
import com.yef.fota.dto.batch.BatchUpgradeStartResponse;
import com.yef.fota.dto.firmware.BatchUpgradeTaskVO;
import com.yef.fota.entity.BatchUpgradeTaskEntity;
import com.yef.fota.entity.DeviceGroupEntity;
import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.mapper.DeviceGroupMapper;
import com.yef.fota.mapper.FirmwarePackageMapper;
import com.yef.fota.service.BatchUpgradeTaskService;

import javax.validation.Valid;

import com.yef.fota.service.DeviceGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/batch-upgrade-tasks")
public class BatchUpgradeTaskController {

    private final BatchUpgradeTaskService batchUpgradeTaskService;
    private final DeviceGroupMapper deviceGroupMapper;
    private final FirmwarePackageMapper firmwarePackageMapper;

    /**
     * 批量升级
     *
     * @param request
     * @return
     */
    @PostMapping("/start")
    @OperationLog(action = "START_BATCH_UPGRADE")
    public ApiResponse<BatchUpgradeStartResponse> start(@RequestBody @Valid BatchUpgradeStartRequest request) {
        return ApiResponse.ok(batchUpgradeTaskService.startBatchUpgrade(request, AuthContext.getUserId()));
    }

    @GetMapping
    public ApiResponse<PageResult<BatchUpgradeTaskVO>> page(@RequestParam(defaultValue = "1") long current,
                                                            @RequestParam(defaultValue = "10") long pageSize,
                                                            @RequestParam(required = false) String status) {
        LambdaQueryChainWrapper<BatchUpgradeTaskEntity> lambdaQuery = batchUpgradeTaskService.lambdaQuery();
        if (org.apache.commons.lang3.StringUtils.isNotBlank(status)) {
            lambdaQuery = lambdaQuery.eq(StringUtils.hasText(status), BatchUpgradeTaskEntity::getStatus, status);
        }

        Page<BatchUpgradeTaskEntity> page = lambdaQuery.orderByDesc(BatchUpgradeTaskEntity::getId)
                .page(new Page<>(current, pageSize));

        List<BatchUpgradeTaskVO> list = new ArrayList<>();
        BatchUpgradeTaskVO batchUpgradeTaskVO;

        if (page.getRecords() != null && page.getRecords().size() > 0) {
            for (BatchUpgradeTaskEntity entity : page.getRecords()) {
                batchUpgradeTaskVO = new BatchUpgradeTaskVO();
                BeanUtils.copyProperties(entity, batchUpgradeTaskVO);
                DeviceGroupEntity deviceGroupEntity = deviceGroupMapper.selectById(entity.getGroupId());
                if (deviceGroupEntity != null) {
                    batchUpgradeTaskVO.setGroupName(deviceGroupEntity.getDeviceGroupName());
                }
                FirmwarePackageEntity packageEntity = firmwarePackageMapper.selectById(entity.getFirmwareId());
                if (packageEntity != null) {
                    batchUpgradeTaskVO.setFirmware(packageEntity.getFileName() + " / " + packageEntity.getVersion());
                }
                list.add(batchUpgradeTaskVO);
            }
        }
        Page<BatchUpgradeTaskVO> batchUpgradeTaskVOPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        batchUpgradeTaskVOPage.setRecords(list);
        return ApiResponse.ok(PageResult.from(batchUpgradeTaskVOPage));
    }
}
