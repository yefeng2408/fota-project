package com.yef.semaphore;

import org.springframework.beans.factory.annotation.Autowired;
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
public class UpgradeSemaphoreService {

    private static final String SEMAPHORE_KEY = "fota:upgrade:holders";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Resource(name = "releaseScript")
    private DefaultRedisScript<Long> releaseScript;


    public void release(String imei) {
        redisTemplate.execute(releaseScript, Collections.singletonList(SEMAPHORE_KEY), imei);
    }


}