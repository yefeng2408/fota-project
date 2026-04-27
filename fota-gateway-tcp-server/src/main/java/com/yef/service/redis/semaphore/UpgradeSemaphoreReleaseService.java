package com.yef.service.redis.semaphore;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * @description: 基于redis实现的分布式信号量，控制同一时刻并发升级设备数量
 * @author: yef
 * @date: 2026/04/27 17:43
 */
@Service
public class UpgradeSemaphoreReleaseService {

    private static final String KEY = "fota:upgrade:holders";

    private final StringRedisTemplate redisTemplate;

    @Resource(name = "releaseScript")
    private DefaultRedisScript<Long> releaseScript;


    public UpgradeSemaphoreReleaseService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void release(String imei) {
        redisTemplate.execute(releaseScript, Collections.singletonList(KEY), imei);
    }


}