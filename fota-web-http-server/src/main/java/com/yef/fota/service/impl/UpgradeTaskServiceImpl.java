package com.yef.fota.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.yef.fota.api.dto.GatewayUpgradeRequest;
import com.yef.fota.api.dto.UpdateDeviceUpgradeFinalResult;
import com.yef.fota.api.service.GatewayCommandService;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.entity.UpgradeTaskEntity;
import com.yef.fota.mapper.UpgradeTaskMapper;
import com.yef.fota.service.FirmwarePackageService;
import com.yef.fota.service.UpgradeTaskService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

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

    private final UpgradeTaskMapper upgradeTaskMapper;
    private final GatewayCommandService gatewayCommandService;

    public UpgradeTaskServiceImpl(UpgradeTaskMapper upgradeTaskMapper, GatewayCommandService gatewayCommandService) {
        this.upgradeTaskMapper = upgradeTaskMapper;
        this.gatewayCommandService = gatewayCommandService;
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
        task.setDeviceId(device.getId());
        task.setImei(device.getImei());
        task.setFirmwareId(firmware.getId());
        task.setBatchId(batchId);
        task.setOperatorId(operatorId);
        task.setTaskStatus("UPGRADE_REQUESTED");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        this.save(task);

        GatewayUpgradeRequest gatewayRequest = getUpgradeRequest(task, device, firmware);

        gatewayCommandService.sendUpgradeRequest(gatewayRequest);
        return task.getId();
    }


    @NotNull
    private GatewayUpgradeRequest getUpgradeRequest(UpgradeTaskEntity task, DeviceEntity device,
                                                    FirmwarePackageEntity firmware) {

        GatewayUpgradeRequest gatewayRequest = new GatewayUpgradeRequest();
        gatewayRequest.setTaskId(task.getTaskId());
        gatewayRequest.setDeviceId(device.getId());
        gatewayRequest.setImei(device.getImei());
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
    public void updateDeviceUpgradeFinalResult(UpdateDeviceUpgradeFinalResult result) {
        UpgradeTaskEntity upgradeTask =new UpgradeTaskEntity();
        BeanUtils.copyProperties(result, upgradeTask);
        Wrapper<UpgradeTaskEntity> queryWrapper = new QueryWrapper<>();

        //this.baseMapper.update();

    }



}
