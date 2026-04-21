package com.yef.fota.service;

import com.yef.fota.dto.device.DeviceSaveRequest;
import com.yef.fota.entity.DeviceEntity;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 设备表 服务类
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
public interface DeviceService extends IService<DeviceEntity> {

    DeviceEntity getDeviceByImei(String imei);

    DeviceEntity addDevice(DeviceSaveRequest request);

    void updateDevice(DeviceEntity entity, DeviceSaveRequest request);
}
