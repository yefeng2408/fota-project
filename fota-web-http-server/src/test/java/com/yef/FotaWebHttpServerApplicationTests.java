package com.yef;

import com.yef.fota.entity.UpgradeTaskEntity;
import com.yef.fota.mapper.UpgradeTaskMapper;
import com.yef.fota.service.UpgradeTaskService;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@SpringBootTest(properties = {
        "fota.rocketmq.upgrade-event-consumer.enabled=false"
})
class FotaWebHttpServerApplicationTests {

    private final MinioClient minioClient;

    FotaWebHttpServerApplicationTests(@Autowired MinioClient minioClient) {
        this.minioClient = minioClient;
    }


    @Test
    void readFirewareAndChunk() {

    }

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private UpgradeTaskMapper upgradeTaskMapper;

    @Test
    public void contextLoads() {

    }

}
