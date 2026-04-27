package com.yef.service.redis.semaphore;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * @description:
 * @author: yef
 * @date: 2026/04/27 21:12
 */
@Configuration
public class RedisScriptConfig {


    @Bean("releaseScript")
    public DefaultRedisScript<Long> releaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                "return redis.call('SREM', KEYS[1], ARGV[1])"
        );
        script.setResultType(Long.class);
        return script;
    }

}