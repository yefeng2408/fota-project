package com.yef.fota.service;

import com.yef.fota.api.dto.CancelUpgradeRequest;
import com.yef.fota.api.dto.resp.DeviceUpgradeCancelEventResult;
import com.yef.fota.api.dto.resp.DeviceUpgradeStartTimeEventResult;
import com.yef.fota.api.dto.resp.UpdateDeviceUpgradeFinalResult;
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

    void updateUpgradeStartTime(DeviceUpgradeStartTimeEventResult result);

    void updateDeviceUpgradeFinalEventResult(UpdateDeviceUpgradeFinalResult result);

    void updateCancelFinalEventResult(DeviceUpgradeCancelEventResult result);



    void cancelUpgrade(CancelUpgradeRequest request);

    UpgradeTaskEntity selectUpgradingTask(String imei);


    int countWaitingTask();

}
