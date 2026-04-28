package com.yef;

import com.dtflys.forest.springboot.annotation.ForestScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableScheduling
@EnableTransactionManagement
@ForestScan(basePackages = "com.yef.fota.api.client")
@SpringBootApplication(excludeName = "com.dtflys.forest.springboot.ForestAutoConfiguration")
public class FotaWebHttpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FotaWebHttpServerApplication.class, args);
    }

}
