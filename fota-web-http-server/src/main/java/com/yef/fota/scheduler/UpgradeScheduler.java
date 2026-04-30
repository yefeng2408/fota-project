package com.yef.fota.scheduler;

import com.yef.fota.api.dto.DeviceUpgradeEventRequest;
import com.yef.fota.api.dto.PlatformUpgradeRequest;
import com.yef.fota.api.service.PlatformCommandService;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.entity.UpgradeTaskEntity;
import com.yef.fota.mapper.FirmwarePackageMapper;
import com.yef.fota.mapper.UpgradeTaskMapper;
import com.yef.fota.redis.semaphore.UpgradeSemaphoreService;
import com.yef.fota.service.DeviceService;
import com.yef.fota.redis.DeviceUpgradeDispatchLockService;
import com.yef.fota.redis.DeviceUpgradeLockService;
import com.yef.fota.service.impl.UpgradeTaskServiceImpl;
import com.yef.fota.websocket.WebSocketConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/*
 * @description: 分批调度(避免多个批量升级任务瞬时打爆网关)
 *
 *  FOTA升级系统采用“调度器 + 分布式信号量 + CAS状态机”的模型控制并发。
 *   1.所有设备先进入 WAITING 队列，调度器按 batchSize 批量拉取任务
 *   2.通过 Redis Set 实现分布式信号量，限制最大并发升级数
 *   3.通过 dispatch-lock 保证单设备调度预占，通过 session-lock 保证升级会话互斥
 *   4.通过数据库 CAS 更新状态，确保只有一个实例真正执行任务，若 CAS 失败，立即释放已抢占资源，防止信号量与预占锁泄漏
 *   5.gateway 受理 0x81 后接管 session-lock；升级完成后由 gateway 释放 session-lock，web 只负责落库与前端广播
 *
 *   完整的升级流转过程：
 *                WAITING
 *                    ↓
 *                等待dispatch()调度 --> (失败 or 超时)
                      ↓                    ↓
                      ↓                  RETRY_WAIT
                      ↓                    ↓
                      ↓                  next_retry_at 到达
                      ↓                    ↓
                      ↓                  重新调度
*                     ↓
 *                    ↓
 *                 UPGRADE_REQUESTED
 *                    ↓
 *                 发送0x81
 *                    ↓
 *                 UPGRADING
 *                    ↓
 *                 WAIT_RESULT
 *                    ↓
 *                 (成功) → SUCCESS
 *
 *
 * @author: yef
 * @date: 2026/04/27 17:44
 */

@Slf4j
@Component
public class UpgradeScheduler implements DisposableBean {

    @Resource
    private UpgradeTaskMapper upgradeTaskMapper;

    @Resource
    private FirmwarePackageMapper firmwarePackageMapper;

    @Resource
    private UpgradeSemaphoreService semaphore;

    @Resource
    private DeviceUpgradeDispatchLockService dispatchLockService;

    @Resource
    private DeviceUpgradeLockService sessionLockService;

    @Resource
    private DeviceService deviceService;

    @Resource
    private PlatformCommandService platformCommandService;

    @Resource
    private WebSocketConfig webSocketConfig;

    @Resource
    private StringRedisTemplate redisTemplate;

    //处于升级中的设备最大数，类似于线程池最大线程数
    @Value("${fota.upgrade.max-active-devices:100}")
    private int maxActive;

    //每次调度最多放多少设备进入升级，类似于线程池的每次 submit 数量
    @Value("${fota.upgrade.dispatch-batch-size:10}")
    private int batchSize;

    private final ThreadPoolExecutor dispatchExecutor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 2,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            r -> {
                Thread thread = new Thread(r);
                thread.setName("fota-upgrade-dispatch-" + thread.getId());
                return thread;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );


    @Scheduled(fixedDelay = 3000)
    public void dispatch() {
        int active = semaphore.current();
        int available = maxActive - active;

        if (available <= 0) {
            return;
        }

        int limit = Math.min(available, batchSize);

        List<UpgradeTaskEntity> tasks = upgradeTaskMapper.selectRunnableTasks(limit);
        if(tasks == null || tasks.isEmpty()) {
            return;
        }

        for (UpgradeTaskEntity taskEntity : tasks) {
            dispatchExecutor.submit(() -> dispatchOneTask(taskEntity));
        }
    }

    /**
     * IO 型任务，采用线程池并行下发0x81指令
     *
     * @param taskEntity
     */
    private void dispatchOneTask(UpgradeTaskEntity taskEntity) {
        String imei = taskEntity.getImei();
        String lockToken = String.valueOf(taskEntity.getTaskId());
        FirmwarePackageEntity packageEntity = firmwarePackageMapper.selectById(taskEntity.getFirmwareId());
        if (packageEntity == null) {
            log.error("调度升级失败，目标固件不存在, imei={}, taskId={}, firmwareId={}",
                    imei, taskEntity.getTaskId(), taskEntity.getFirmwareId());
            return;
        }

        // 1.获取信号量
        if (!semaphore.tryAcquire(imei, maxActive)) {
            return;
        }

        // 2.调度预占锁
        boolean locked = dispatchLockService.acquireLock(imei, lockToken);
        if (!locked) {
            //若当前线程未能抢到设备升级资格，则释放前面抢到的信号量资源。避免浪费信号量资源
            semaphore.release(imei);
            return;
        }

        // 3.CAS更新状态
        int updated = upgradeTaskMapper.casToRequested(taskEntity.getId());
        if (updated == 0) {
            // 如果当前线程 CAS 失败，则释放刚刚抢到的信号量资源和 dispatch-lock，防止信号量“假满”。
            dispatchLockService.releaseLock(imei, lockToken);
            semaphore.release(imei);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        deviceService.lambdaUpdate()
                .eq(DeviceEntity::getId, taskEntity.getDeviceId())
                .set(DeviceEntity::getDeviceUpgradeStatus, "UPGRADE_REQUESTED")
                .set(DeviceEntity::getLastUpgradeTaskId, taskEntity.getTaskId())
                .set(DeviceEntity::getUpdatedAt, now)
                .update();

        webSocketConfig.pushDeviceUpgradeEvent(new DeviceUpgradeEventRequest(
                imei,
                "UPGRADE_REQUESTED",
                0,
                null,
                packageEntity.getVersion()
        ));

        try {
            long start = System.currentTimeMillis();
            long beforeHttp = System.currentTimeMillis();

            // 4.下发升级请求（0x81）
            DeviceEntity deviceEntity = new DeviceEntity();
            deviceEntity.setId(taskEntity.getDeviceId());
            deviceEntity.setImei(imei);
            PlatformUpgradeRequest upgradeRequest = UpgradeTaskServiceImpl.getUpgradeRequest(taskEntity, deviceEntity, packageEntity);

            platformCommandService.sendUpgradeRequest(upgradeRequest);
            dispatchLockService.releaseLock(imei, lockToken);

            long afterHttp = System.currentTimeMillis();
            long total = afterHttp - start;
            long httpCost = afterHttp - beforeHttp;
            long localCost = total - httpCost;

            log.info("dispatchOneTask cost, imei={}, total={}ms, http={}ms, local={}ms", imei, total, httpCost, localCost);
        } catch (RuntimeException ex) {
            dispatchLockService.releaseLock(imei, lockToken);
            //如果设备锁已存在，则表明设备处于升级过程中。直接return，不能重试
            if (gatewayAccepted(imei, lockToken)) {
                log.warn("dispatchOneTask 响应异常，但 gateway 已受理任务，imei={}, taskId={}, error={}",
                        imei, taskEntity.getTaskId(), ex.getMessage());
                return;
            }

            handleRetry(taskEntity, ex.getMessage());
            semaphore.release(imei);
            log.warn("------>dispatchOneTask error:{}", ex.getMessage());
        }
    }


    /**
     * handleRetry 不是针对“没抢到名额”的设备。没抢到信号量/dispatch-lock 的任务应该继续保持 WAITING，不进入 retry。
     *
     * handleRetry 针对的是：
     * 已经抢到信号量 + dispatch-lock + CAS 改成 UPGRADE_REQUESTED，但是调用（http）网关下发 0x81 失败，
     * 且确认 gateway 尚未受理此次升级任务。
     *
     * @param task
     * @param errorMsg
     */
    private void handleRetry(UpgradeTaskEntity task, String errorMsg) {

        Integer retryCount = task.getRetryCount()==null?0:task.getRetryCount();
        int maxRetry = task.getMaxRetry()==null?0:task.getMaxRetry();

        if (retryCount >= maxRetry) {
            // ❌ 超过最大重试次数 → FAIL。并推送 FAIL 状态事件
            webSocketConfig.pushDeviceUpgradeEvent(new DeviceUpgradeEventRequest(
                    task.getImei(),
                    "FAIL",
                    0,
                    null,
                    null
            ));
            upgradeTaskMapper.markFail(task.getId(), errorMsg);
            //TODO 这里是否要做最终一致性更新device表的 device_upgrade_status 设备升级状态字段

            log.error("任务最终失败 taskId={}, error={}", task.getTaskId(), errorMsg);
            return;
        }

        // 计算指数退避
        int nextRetry = retryCount + 1;

        long delaySeconds = (long) (5 * Math.pow(2, retryCount)); // base=5s
        delaySeconds = Math.min(delaySeconds, 300); // 最大5分钟

        //加上随机时间s，防止selectRunnableTasks(limit)扫描到同一时间大批次的任务，同时下发0x81给网关造成压力
        delaySeconds += ThreadLocalRandom.current().nextInt(3);
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);

        upgradeTaskMapper.updateRetry(
                task.getId(),
                nextRetry,
                nextRetryAt,
                errorMsg
        );
        //推送 RETRY_WAITING 状态事件
        webSocketConfig.pushDeviceUpgradeEvent(new DeviceUpgradeEventRequest(
                task.getImei(),
                "RETRY_WAITING",
                0,
                null,
                null
        ));
        log.warn("任务进入重试 taskId={}, retryCount={}, nextRetryAt={}", task.getTaskId(), nextRetry, nextRetryAt);
    }

    private boolean gatewayAccepted(String imei, String lockToken) {
        if (sessionLockService.isHeldBy(imei, lockToken)) {
            return true;
        }
        Object runtimeTaskId = redisTemplate.opsForHash().get("fota:upgrade:runtime:" + imei, "taskId");
        return runtimeTaskId != null && lockToken.equals(String.valueOf(runtimeTaskId));
    }


    @Override
    public void destroy() {
        dispatchExecutor.shutdown();
    }
}
