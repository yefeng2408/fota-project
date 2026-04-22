package com.yef.fota.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yef.fota.dto.batch.BatchUpgradeStartRequest;
import com.yef.fota.dto.batch.BatchUpgradeStartResponse;
import com.yef.fota.entity.BatchUpgradeTaskEntity;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.DeviceFirmwareBindingEntity;
import com.yef.fota.entity.DeviceGroupEntity;
import com.yef.fota.entity.DeviceGroupRelationEntity;
import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.exception.BusinessException;
import com.yef.fota.mapper.BatchUpgradeTaskMapper;
import com.yef.fota.service.DeviceFirmwareBindingService;
import com.yef.fota.service.DeviceGroupRelationService;
import com.yef.fota.service.DeviceGroupService;
import com.yef.fota.service.DeviceService;
import com.yef.fota.service.FirmwarePackageService;
import com.yef.fota.service.BatchUpgradeTaskService;
import com.yef.fota.service.UpgradeTaskService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * <p>
 * 批量升级任务表 服务实现类
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Service
public class BatchUpgradeTaskServiceImpl extends ServiceImpl<BatchUpgradeTaskMapper, BatchUpgradeTaskEntity> implements BatchUpgradeTaskService {

    private final DeviceGroupService deviceGroupService;
    private final DeviceGroupRelationService deviceGroupRelationService;
    private final DeviceService deviceService;
    private final FirmwarePackageService firmwarePackageService;
    private final DeviceFirmwareBindingService deviceFirmwareBindingService;
    private final UpgradeTaskService upgradeTaskService;

    public BatchUpgradeTaskServiceImpl(DeviceGroupService deviceGroupService,
                                       DeviceGroupRelationService deviceGroupRelationService,
                                       DeviceService deviceService,
                                       FirmwarePackageService firmwarePackageService,
                                       DeviceFirmwareBindingService deviceFirmwareBindingService,
                                       UpgradeTaskService upgradeTaskService) {
        this.deviceGroupService = deviceGroupService;
        this.deviceGroupRelationService = deviceGroupRelationService;
        this.deviceService = deviceService;
        this.firmwarePackageService = firmwarePackageService;
        this.deviceFirmwareBindingService = deviceFirmwareBindingService;
        this.upgradeTaskService = upgradeTaskService;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public BatchUpgradeStartResponse startBatchUpgrade(BatchUpgradeStartRequest request, Long operatorId) {
        DeviceGroupEntity group = deviceGroupService.getById(request.getGroupId());
        if (group == null) {
            throw new BusinessException("设备组不存在");
        }

        FirmwarePackageEntity firmware = firmwarePackageService.getById(request.getFirmwareId());
        if (firmware == null) {
            throw new BusinessException("目标固件不存在");
        }

        List<DeviceGroupRelationEntity> relations = deviceGroupRelationService.lambdaQuery()
                .eq(DeviceGroupRelationEntity::getDeviceGroupId, request.getGroupId())
                .list();
        if (relations.isEmpty()) {
            throw new BusinessException("当前设备组下没有设备，无法批量升级");
        }

        Set<Long> deviceIds = relations.stream()
                .map(DeviceGroupRelationEntity::getDeviceId)
                .collect(Collectors.toSet());
        List<DeviceEntity> devices = deviceIds.isEmpty()
                ? Collections.emptyList()
                : deviceService.listByIds(deviceIds);
        if (devices.isEmpty()) {
            throw new BusinessException("当前设备组下没有可用设备，无法批量升级");
        }

        BatchUpgradeTaskEntity batchTask = new BatchUpgradeTaskEntity();
        batchTask.setGroupId(group.getId());
        batchTask.setFirmwareId(firmware.getId());
        batchTask.setStatus("RUNNING");
        batchTask.setTotalCount(devices.size());
        batchTask.setSuccessCount(0);
        batchTask.setFailCount(0);
        batchTask.setCreatedBy(operatorId);
        batchTask.setCreatedAt(LocalDateTime.now());
        this.save(batchTask);

        Map<Long, DeviceFirmwareBindingEntity> currentBindingMap = deviceFirmwareBindingService.lambdaQuery()
                .in(DeviceFirmwareBindingEntity::getDeviceId, deviceIds)
                .eq(DeviceFirmwareBindingEntity::getFirmwareId, firmware.getId())
                .list()
                .stream()
                .collect(Collectors.toMap(DeviceFirmwareBindingEntity::getDeviceId, Function.identity(), (a, b) -> a));

        int startedCount = 0;
        int skippedCount = 0;
        for (DeviceEntity device : devices) {
            if (!canBatchUpgrade(device, firmware)) {
                skippedCount++;
                continue;
            }

            bindFirmwareForBatch(device, firmware, operatorId, currentBindingMap.get(device.getId()));
            upgradeTaskService.startUpgrade(device, firmware, batchTask.getId(), operatorId);
            markBindingTriggered(device.getId(), firmware.getId());
            startedCount++;
        }

        batchTask.setStatus("FINISHED");
        batchTask.setSuccessCount(startedCount);
        batchTask.setFailCount(skippedCount);
        this.updateById(batchTask);

        BatchUpgradeStartResponse response = new BatchUpgradeStartResponse();
        response.setBatchTaskId(batchTask.getId());
        response.setGroupId(group.getId());
        response.setFirmwareId(firmware.getId());
        response.setTotalCount(devices.size());
        response.setStartedCount(startedCount);
        response.setSkippedCount(skippedCount);
        response.setSummary(String.format("批量升级已提交：共 %d 台，成功发起 %d 台，跳过 %d 台", devices.size(), startedCount, skippedCount));
        return response;
    }

    private boolean canBatchUpgrade(DeviceEntity device, FirmwarePackageEntity firmware) {
        if (device == null) {
            return false;
        }
        if (StringUtils.hasText(firmware.getDeviceType())
                && StringUtils.hasText(device.getDeviceType())
                /*&& !firmware.getDeviceType().equals(device.getDeviceType())*/) {
            return false;
        }
        return "NO_TASK".equals(device.getDeviceUpgradeStatus());
    }

    private void bindFirmwareForBatch(DeviceEntity device,
                                      FirmwarePackageEntity firmware,
                                      Long operatorId,
                                      DeviceFirmwareBindingEntity currentBinding) {
        LocalDateTime now = LocalDateTime.now();

        deviceService.update(new LambdaUpdateWrapper<DeviceEntity>()
                .eq(DeviceEntity::getId, device.getId())
                .set(DeviceEntity::getTargetFirmwareId, firmware.getId())
                .set(DeviceEntity::getIsBind, 1)
                .set(DeviceEntity::getUpdatedAt, now));

        deviceFirmwareBindingService.update(new LambdaUpdateWrapper<DeviceFirmwareBindingEntity>()
                .eq(DeviceFirmwareBindingEntity::getDeviceId, device.getId())
                .ne(DeviceFirmwareBindingEntity::getFirmwareId, firmware.getId())
                .in(DeviceFirmwareBindingEntity::getBindStatus, List.of("BOUND", "TRIGGERED"))
                .set(DeviceFirmwareBindingEntity::getBindStatus, "CANCELED")
                .set(DeviceFirmwareBindingEntity::getTriggered, false)
                .set(DeviceFirmwareBindingEntity::getUnboundAt, now)
                .set(DeviceFirmwareBindingEntity::getUpdatedAt, now));

        if (currentBinding == null) {
            currentBinding = new DeviceFirmwareBindingEntity();
            currentBinding.setDeviceId(device.getId());
            currentBinding.setFirmwareId(firmware.getId());
            currentBinding.setOperatorId(operatorId);
            currentBinding.setBindStatus("BOUND");
            currentBinding.setTriggered(false);
            currentBinding.setBoundAt(now);
            currentBinding.setCreatedAt(now);
            currentBinding.setUpdatedAt(now);
            deviceFirmwareBindingService.save(currentBinding);
            return;
        }

        currentBinding.setOperatorId(operatorId);
        currentBinding.setBindStatus("BOUND");
        currentBinding.setTriggered(false);
        currentBinding.setTriggeredAt(null);
        currentBinding.setUnboundAt(null);
        currentBinding.setBoundAt(now);
        currentBinding.setUpdatedAt(now);
        deviceFirmwareBindingService.updateById(currentBinding);
    }

    private void markBindingTriggered(Long deviceId, Long firmwareId) {
        LocalDateTime now = LocalDateTime.now();
        deviceFirmwareBindingService.update(new LambdaUpdateWrapper<DeviceFirmwareBindingEntity>()
                .eq(DeviceFirmwareBindingEntity::getDeviceId, deviceId)
                .eq(DeviceFirmwareBindingEntity::getFirmwareId, firmwareId)
                .set(DeviceFirmwareBindingEntity::getBindStatus, "TRIGGERED")
                .set(DeviceFirmwareBindingEntity::getTriggered, true)
                .set(DeviceFirmwareBindingEntity::getTriggeredAt, now)
                .set(DeviceFirmwareBindingEntity::getUpdatedAt, now));
    }
}
