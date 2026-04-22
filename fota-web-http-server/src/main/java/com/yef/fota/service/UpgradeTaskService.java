package com.yef.fota.service;

import com.yef.fota.api.dto.UpdateDeviceUpgradeFinalResult;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.entity.UpgradeTaskEntity;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 设备升级任务表 服务类
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
public interface UpgradeTaskService extends IService<UpgradeTaskEntity> {

    Long startUpgrade(DeviceEntity deviceEntity,FirmwarePackageEntity firmware);

    Long startUpgrade(DeviceEntity deviceEntity, FirmwarePackageEntity firmware, Long batchId, Long operatorId);

    void updateDeviceUpgradeFinalResult(UpdateDeviceUpgradeFinalResult result);
}
