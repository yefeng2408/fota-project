package com.yef.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Slf4j
@Component
public class OfflineDeviceScheduler {

    @Autowired
    private StringRedisTemplate redisTemplate;

    static final String prefix = "mock-dev:upgrade:runtime:";
    static final String pattern = prefix + "*";
    //6分钟
    static final Long expireTime = (long) (1000 * 60 * 6);

    public List<String> getKeysByPattern(String pattern) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)      // 设置匹配模式，如 "mock-dev:upgrade:runtime:*"
                .count(500)          // 每次迭代返回的大约数量（建议 100-1000）
                .build();

        // 使用 stringRedisTemplate 执行 SCAN
        try (Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(
                redisConnection -> redisConnection.scan(options))) {
            while (cursor.hasNext()) {
                String key = new String(cursor.next());
                keys.add(key);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return keys;
    }


    @Scheduled(fixedDelay = 1000 * 60 * 1)
    public void dispatch() {
        List<String> keys = getKeysByPattern(pattern);
        if (!CollectionUtils.isEmpty(keys)) {
            List<String> needDeleteKeys=null;
            for (String key : keys) {
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
                if(CollectionUtils.isEmpty(entries)){continue;}

                if(entries.containsKey("receivePacketAt")){
                    needDeleteKeys = new ArrayList<>();
                    long receivePacketAt = Long.parseLong(String.valueOf(entries.get("receivePacketAt")));
                    if(System.currentTimeMillis() - receivePacketAt > expireTime){
                        needDeleteKeys.add(key);
                    }
                }
            }
            if(!CollectionUtils.isEmpty(needDeleteKeys)){
                redisTemplate.delete(needDeleteKeys);
            }
        }

    }


}
