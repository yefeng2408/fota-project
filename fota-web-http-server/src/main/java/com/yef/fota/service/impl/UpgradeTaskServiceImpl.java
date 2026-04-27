package com.yef.fota.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.yef.fota.api.dto.CancelUpgradeRequest;
import com.yef.fota.api.dto.PlatformCancelUpgradeRequest;
import com.yef.fota.api.dto.PlatformUpgradeRequest;
import com.yef.fota.api.dto.resp.DeviceUpgradeCancelEventResult;
import com.yef.fota.api.dto.resp.DeviceUpgradeStartTimeEventResult;
import com.yef.fota.api.dto.resp.UpdateDeviceUpgradeFinalResult;
import com.yef.fota.api.service.PlatformCommandService;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.entity.UpgradeTaskEntity;
import com.yef.fota.mapper.UpgradeTaskMapper;
import com.yef.fota.redis.semaphore.UpgradeSemaphoreService;
import com.yef.fota.service.DeviceService;
import com.yef.fota.service.DeviceUpgradeLockService;
import com.yef.fota.service.UpgradeTaskService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yef.fota.exception.BusinessException;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.springframework.util.StringUtils;

/**
 * <p>
 * 设备升级任务表 服务实现类
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Service
public class UpgradeTaskServiceImpl extends ServiceImpl<UpgradeTaskMapper, UpgradeTaskEntity> implements UpgradeTaskService {

    private final PlatformCommandService platformCommandService;
    private final DeviceUpgradeLockService deviceUpgradeLockService;
    private final UpgradeSemaphoreService upgradeSemaphoreService;
    private final DeviceService deviceService;
    private final UpgradeTaskMapper upgradeTaskMapper;

    public UpgradeTaskServiceImpl(PlatformCommandService platformCommandService,
                                  DeviceUpgradeLockService deviceUpgradeLockService,
                                  UpgradeSemaphoreService upgradeSemaphoreService,
                                  DeviceService deviceService,
                                  UpgradeTaskMapper upgradeTaskMapper) {

        this.platformCommandService = platformCommandService;
        this.deviceUpgradeLockService = deviceUpgradeLockService;
        this.upgradeSemaphoreService = upgradeSemaphoreService;
        this.deviceService = deviceService;
        this.upgradeTaskMapper = upgradeTaskMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long startUpgrade(DeviceEntity device,FirmwarePackageEntity firmware) {
        return startUpgrade(device, firmware, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long startUpgrade(DeviceEntity device, FirmwarePackageEntity firmware, Long batchId, Long operatorId) {
        UpgradeTaskEntity task = new UpgradeTaskEntity();
        //雪花id，保证全局唯一
        task.setTaskId(IdWorker.getId());
        String lockToken = String.valueOf(task.getTaskId());

       /* boolean lockAcquired = deviceUpgradeLockService.acquireLock(device.getImei(), lockToken);
        if (!lockAcquired) {
            throw new BusinessException("设备升级流程已在执行中，请勿重复下发升级指令");
        }*/
        task.setDeviceId(device.getId());
        task.setImei(device.getImei());
        task.setFirmwareId(firmware.getId());
        task.setBatchId(batchId);
        task.setOperatorId(operatorId);
        task.setTaskStatus("WAITING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        try {
            boolean saved = this.save(task);
            if (!saved) {
                throw new BusinessException("创建升级任务失败");
            }

            deviceService.update(new LambdaUpdateWrapper<DeviceEntity>()
                    .eq(DeviceEntity::getId, device.getId())
                    .set(DeviceEntity::getDeviceUpgradeStatus, "WAITING")
                    .set(DeviceEntity::getLastUpgradeTaskId, task.getTaskId())
                    .set(DeviceEntity::getUpdatedAt, LocalDateTime.now()));

            return task.getTaskId();
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    @Override
    public void updateUpgradeStartTime(DeviceUpgradeStartTimeEventResult result) {
        upgradeTaskMapper.updateTaskStartTime(result.getTaskId(),result.getStartTime());
    }


    @NotNull
    public static PlatformUpgradeRequest getUpgradeRequest(UpgradeTaskEntity task, DeviceEntity device,
                                                    FirmwarePackageEntity firmware) {

        PlatformUpgradeRequest gatewayRequest = new PlatformUpgradeRequest();
        gatewayRequest.setTaskId(task.getTaskId());
        gatewayRequest.setDeviceId(device.getId());
        gatewayRequest.setImei(device.getImei());
        gatewayRequest.setLockToken(String.valueOf(task.getTaskId()));
        gatewayRequest.setFirmwareId(firmware.getId());

        byte[] firmwareNameBytes = firmware.getFileName().getBytes(StandardCharsets.UTF_8);
        byte[] firmwareVersionBytes = firmware.getVersion().getBytes(StandardCharsets.UTF_8);

        gatewayRequest.setFirmwareNameLen((byte) firmwareNameBytes.length);
        gatewayRequest.setFirmwareName(firmware.getFileName());

        gatewayRequest.setFirmwareVersionLen((byte) firmwareVersionBytes.length);
        gatewayRequest.setFirmwareVersionName(firmware.getVersion());

        gatewayRequest.setChunkSize(firmware.getChunkSize());
        gatewayRequest.setTotalPacket(firmware.getTotalPacket());
        gatewayRequest.setFileSize(firmware.getFileSize());
        gatewayRequest.setMd5(firmware.getMd5());
        gatewayRequest.setBucketName(firmware.getBucketName());
        gatewayRequest.setObjectName(firmware.getObjectName());
        return gatewayRequest;
    }



    @Override
    public void updateDeviceUpgradeFinalEventResult(UpdateDeviceUpgradeFinalResult result) {
        if (result == null || !StringUtils.hasText(result.getImei()) || !StringUtils.hasText(result.getTaskId())) {
            return;
        }

        deviceUpgradeLockService.releaseLock(result.getImei(), result.getTaskId());
        upgradeSemaphoreService.release(result.getImei());

        Long taskId;
        try {
            taskId = Long.valueOf(result.getTaskId());
        } catch (NumberFormatException ex) {
            return;
        }

        LocalDateTime now = result.getEndTime() == null ? LocalDateTime.now() : result.getEndTime();
        this.update(new LambdaUpdateWrapper<UpgradeTaskEntity>()
                .eq(UpgradeTaskEntity::getTaskId, taskId)
                .set(StringUtils.hasText(result.getTaskStatus()), UpgradeTaskEntity::getTaskStatus, result.getTaskStatus())
                .set(result.getProgress() != null, UpgradeTaskEntity::getProgress, result.getProgress())
                .set(result.getCurrentPacket() > 0, UpgradeTaskEntity::getCurrentPacket, result.getCurrentPacket())
                .set(result.getTotalPacket() > 0, UpgradeTaskEntity::getTotalPacket, result.getTotalPacket())
                .set(StringUtils.hasText(result.getFailReason()), UpgradeTaskEntity::getFailReason, result.getFailReason())
                .set(UpgradeTaskEntity::getEndTime, now)
                .set(UpgradeTaskEntity::getUpdatedAt, now));

        deviceService.update(new LambdaUpdateWrapper<DeviceEntity>()
                .eq(DeviceEntity::getImei, result.getImei())
                .set(StringUtils.hasText(result.getTaskStatus()), DeviceEntity::getDeviceUpgradeStatus, result.getTaskStatus())
                .set("SUCCESS".equals(result.getTaskStatus()) && StringUtils.hasText(result.getTargetFirmwareVersion()),
                        DeviceEntity::getCurrentFirmwareVersion,
                        result.getTargetFirmwareVersion())
                .set(DeviceEntity::getUpdatedAt, now));
    }


    @Override
    public void updateCancelFinalEventResult(DeviceUpgradeCancelEventResult result) {
        if (result == null || !StringUtils.hasText(result.getImei())) {
            return;
        }

        UpgradeTaskEntity upgradeTask = this.baseMapper.selectUpgradingTaskByImei(result.getImei());
        if (upgradeTask != null && upgradeTask.getTaskId() != null) {
            deviceUpgradeLockService.releaseLock(result.getImei(), String.valueOf(upgradeTask.getTaskId()));
            this.update(new LambdaUpdateWrapper<UpgradeTaskEntity>()
                    .eq(UpgradeTaskEntity::getTaskId, upgradeTask.getTaskId())
                    .set(StringUtils.hasText(result.getStatus()), UpgradeTaskEntity::getTaskStatus, result.getStatus())
                    .set(UpgradeTaskEntity::getEndTime, LocalDateTime.now())
                    .set(UpgradeTaskEntity::getUpdatedAt, LocalDateTime.now()));
        }
        upgradeSemaphoreService.release(result.getImei());

        LocalDateTime now = LocalDateTime.now();
        deviceService.update(new LambdaUpdateWrapper<DeviceEntity>()
                .eq(DeviceEntity::getImei, result.getImei())
                .set(StringUtils.hasText(result.getStatus()), DeviceEntity::getDeviceUpgradeStatus, result.getStatus())
                .set(DeviceEntity::getUpdatedAt, now));
    }

    @Override
    public void cancelUpgrade(CancelUpgradeRequest request) {
        PlatformCancelUpgradeRequest cancelUpgradeRequest = new PlatformCancelUpgradeRequest();
        BeanUtils.copyProperties(request, cancelUpgradeRequest);
        platformCommandService.sendCancelUpgradeRequest(cancelUpgradeRequest);
    }


    @Override
    public UpgradeTaskEntity selectUpgradingTask(String imei) {
        return this.baseMapper.selectUpgradingTaskByImei(imei);
    }


    @Override
    public int countWaitingTask(){
        return this.baseMapper.countWaitingTask();
    }
}
