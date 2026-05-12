package com.yef;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FotaMockDeviceClientApplication {

    public static void main(String[] args) throws Exception {
        Thread.sleep(2000);
        SpringApplication.run(FotaMockDeviceClientApplication.class, args);
    }

}
