package com.wms.outbound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OutboundServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OutboundServiceApplication.class, args);
    }
}
