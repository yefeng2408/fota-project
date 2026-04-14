package com.yef;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FotaWebHttpServerApplicationTests {

    private final MinioClient minioClient;

    FotaWebHttpServerApplicationTests(@Autowired MinioClient minioClient) {
        this.minioClient = minioClient;
    }


    @Test
    void readFirewareAndChunk() {
        minioClient.

    }

}
