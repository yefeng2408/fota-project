package com.yef.fota.service.impl;

import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.mapper.FirmwarePackageMapper;
import com.yef.fota.service.FirmwarePackageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 固件包信息表 服务实现类
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Service
public class FirmwarePackageServiceImpl extends ServiceImpl<FirmwarePackageMapper, FirmwarePackageEntity> implements FirmwarePackageService {

    @Autowired
    private FirmwarePackageMapper firmwarePackageMapper;

    @Override
    public String existVersion(String version) {
        return firmwarePackageMapper.selectByFirmwareVersion(version);
    }
}
