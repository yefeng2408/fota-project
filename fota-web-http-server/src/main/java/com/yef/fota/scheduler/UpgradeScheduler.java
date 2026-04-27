package com.yef.fota.scheduler;

import com.yef.fota.api.dto.PlatformUpgradeRequest;
import com.yef.fota.api.service.PlatformCommandService;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.entity.UpgradeTaskEntity;
import com.yef.fota.exception.BusinessException;
import com.yef.fota.mapper.FirmwarePackageMapper;
import com.yef.fota.mapper.UpgradeTaskMapper;
import com.yef.fota.redis.semaphore.UpgradeSemaphoreService;
import com.yef.fota.service.DeviceUpgradeLockService;
import com.yef.fota.service.impl.UpgradeTaskServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.List;

/**
 * @description: 分批调度(避免d多个批量升级任务瞬时打爆网关)
 *
 *  FOTA升级系统采用“调度器 + 分布式信号量 + CAS状态机”的模型控制并发。
 *   所有设备先进入 WAITING 队列
 *   调度器按 batchSize 批量拉取任务
 *   通过 Redis Set 实现分布式信号量，限制最大并发升级数
 *   通过 Redis 分布式锁保证单设备串行升级
 *   通过数据库 CAS 更新状态，确保只有一个实例真正执行任务
 *   若 CAS 失败，立即释放已抢占资源，防止信号量与锁泄漏
 *   升级完成后，由网关根据设备上报结果释放信号量与设备锁
 *   同时对 WAITING 队列做限流保护，避免任务堆积
 *
 * @author: yef
 * @date: 2026/04/27 17:44
 */
@Slf4j
@Component
public class UpgradeScheduler {

    @Resource
    private UpgradeTaskMapper taskMapper;

    @Resource
    private FirmwarePackageMapper firmwarePackageMapper;

    @Resource
    private UpgradeSemaphoreService semaphore;

    @Resource
    private DeviceUpgradeLockService lockService;

    @Resource
    private PlatformCommandService platformCommandService;

    //处于升级中的设备最大数，类似于线程池最大线程数
    @Value("${fota.upgrade.max-active-devices:500}")
    private int maxActive;

    //每次调度最多放多少设备进入升级，类似于线程池的每次 submit 数量
    @Value("${fota.upgrade.dispatch-batch-size:50}")
    private int batchSize;


    @Scheduled(fixedDelay = 3000)
    public void dispatch() {
        log.info("---->触发dispatch");
        int active = semaphore.current();
        int available = maxActive - active;

        if (available <= 0) {
            return;
        }

        int limit = Math.min(available, batchSize);

        List<UpgradeTaskEntity> tasks = taskMapper.selectRunnableTasks(limit);
        if(tasks == null || tasks.isEmpty()) {
            return;
        }

        //目标固件
        FirmwarePackageEntity packageEntity = firmwarePackageMapper.selectById(tasks.get(0).getFirmwareId());
        if(packageEntity==null){
            throw new BusinessException("目标固件不存在");
        }

        for (UpgradeTaskEntity taskEntity : tasks) {

            String imei = taskEntity.getImei();

            // 1️⃣ 获取信号量
            if (!semaphore.tryAcquire(imei, maxActive)) {
                continue;
            }

            // 2️⃣ 单设备锁
            boolean locked = lockService.acquireLock(imei, taskEntity.getImei());
            if (!locked) {
                //若未能抢到设备升级资格，则释放前面抢到的信号量资源。避免浪费信号量资源
                semaphore.release(imei);
                continue;
            }

            // 3️⃣ CAS更新状态
            int updated = taskMapper.casToRequested(taskEntity.getId());
            if (updated == 0) {
                //如果当前线程CAS失败，则释放刚刚抢到的 信号量资源和 设备锁。防止信号量“假满”【semaphore fake full】
                //dispatch 释放锁，并不是释放“正在升级的设备锁”，只是释放名额
                lockService.releaseLock(imei, String.valueOf(taskEntity.getTaskId()));
                semaphore.release(imei);
                continue;
            }

            // 4️⃣ 下发升级请求（0x81）
            DeviceEntity deviceEntity = new DeviceEntity();
            deviceEntity.setId(taskEntity.getDeviceId());
            deviceEntity.setImei(imei);
            PlatformUpgradeRequest upgradeRequest = UpgradeTaskServiceImpl.getUpgradeRequest(taskEntity, deviceEntity, packageEntity);

            platformCommandService.sendUpgradeRequest(upgradeRequest);
            log.info("调度升级设备 imei={}", imei);
        }
    }
}