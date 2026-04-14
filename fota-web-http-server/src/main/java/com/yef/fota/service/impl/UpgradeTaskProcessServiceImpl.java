package com.yef.fota.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yef.fota.entity.UpgradeTaskProcessEntity;
import com.yef.fota.mapper.UpgradeTaskProcessMapper;
import com.yef.fota.service.UpgradeTaskProcessService;
import org.springframework.stereotype.Service;

@Service
public class UpgradeTaskProcessServiceImpl extends ServiceImpl<UpgradeTaskProcessMapper, UpgradeTaskProcessEntity>
        implements UpgradeTaskProcessService {
}
