package com.yef;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FotaGatewayTcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FotaGatewayTcpServerApplication.class, args);
    }

}
