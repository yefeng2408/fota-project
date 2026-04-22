package com.yef.fota.service;

import com.yef.fota.entity.BatchUpgradeTaskEntity;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yef.fota.dto.batch.BatchUpgradeStartRequest;
import com.yef.fota.dto.batch.BatchUpgradeStartResponse;

/**
 * <p>
 * 批量升级任务表 服务类
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
public interface BatchUpgradeTaskService extends IService<BatchUpgradeTaskEntity> {

    BatchUpgradeStartResponse startBatchUpgrade(BatchUpgradeStartRequest request, Long operatorId);
}
