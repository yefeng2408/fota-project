package com.yef.fota.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yef.fota.entity.DeviceFirmwareBindingEntity;
import com.yef.fota.mapper.DeviceFirmwareBindingMapper;
import com.yef.fota.service.DeviceFirmwareBindingService;
import org.springframework.stereotype.Service;

@Service
public class DeviceFirmwareBindingServiceImpl extends ServiceImpl<DeviceFirmwareBindingMapper, DeviceFirmwareBindingEntity>
        implements DeviceFirmwareBindingService {
}
