package com.yef.fota.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.mapper.DeviceMapper;
import com.yef.fota.service.DeviceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 设备表 服务实现类
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, DeviceEntity> implements DeviceService {

    @Override
    public DeviceEntity getDeviceByImei(String imei) {
        QueryWrapper<DeviceEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("imei", imei);
        return this.baseMapper.selectOne(queryWrapper);
    }
}
