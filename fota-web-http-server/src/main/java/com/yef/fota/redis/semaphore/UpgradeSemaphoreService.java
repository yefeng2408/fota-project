package com.yef.fota.redis.semaphore;

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

    private static final String KEY = "fota:upgrade:holders";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Resource(name = "acquireScript")
    private DefaultRedisScript<Long> acquireScript;

    @Resource(name = "releaseScript")
    private DefaultRedisScript<Long> releaseScript;


    //获取锁
    public boolean tryAcquire(String imei, int max) {
        Long result = redisTemplate.execute(
                acquireScript,
                Collections.singletonList(KEY),
                String.valueOf(max),
                imei
        );
        return result != null && result == 1;
    }


    public void release(String imei) {
        redisTemplate.execute(releaseScript, Collections.singletonList(KEY), imei);
    }

    public int current() {
        return redisTemplate.opsForSet().size(KEY).intValue();
    }


}