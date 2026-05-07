package com.yef.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * @description: 调度器释放引用计数为0固件缓存对象
 * @author: 叶丰
 * @date: 2026/5/7 10:47
 */
@Slf4j
@Component
public class FirmwareCacheScheduler {

    //5分钟
    private static final long min = 1000 * 60 * 5;

    @Autowired
    private FirmwareCacheManager firmwareCacheManager;

    //每分钟调度一次
    @Scheduled(fixedDelay = 1000 * 60)
    public void clearIdleFirmwareCache() {
        Map<Long, FirmwareCacheHolder> firmwareCacheHolderMap = firmwareCacheManager.firmwareCache();
        for (Map.Entry<Long, FirmwareCacheHolder> entry : firmwareCacheHolderMap.entrySet()) {
            FirmwareCacheHolder firmwareCacheHolder = entry.getValue();
            if(firmwareCacheHolder.getFirmwareId()!=null){
                long lastAccessAt = firmwareCacheHolder.getLastAccessAt();
                if(System.currentTimeMillis() - lastAccessAt > min
                        && firmwareCacheHolder.getRefCount().get()<=0){

                    firmwareCacheManager.remove(firmwareCacheHolder.getFirmwareId());
                    log.info("==========>FirmwareCacheScheduler|clearIdleFirmwareCache firmwareCacheManager size={}", firmwareCacheManager.size());
                }
            }
        }
    }

}