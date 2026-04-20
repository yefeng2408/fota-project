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

@SpringBootTest
class FotaWebHttpServerApplicationTests {

    private final MinioClient minioClient;

    FotaWebHttpServerApplicationTests(@Autowired MinioClient minioClient) {
        this.minioClient = minioClient;
    }


    @Test
    void readFirewareAndChunk() {
       // minioClient.

    }

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private UpgradeTaskMapper upgradeTaskMapper;

    @Test
    public void contextLoads() {
        redisTemplate.delete("fota:upgrade:runtime:66666666");
        List<UpgradeTaskEntity> upgradeTaskEntities = upgradeTaskMapper.selectAll();
        if(!upgradeTaskEntities.isEmpty()){
            upgradeTaskMapper.delByTaskId(upgradeTaskEntities.get(0).getTaskId());
            System.out.println("========>delByTaskId success");
        }

    }

}
