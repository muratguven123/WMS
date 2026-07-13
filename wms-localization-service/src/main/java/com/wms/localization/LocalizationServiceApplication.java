package com.wms.localization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class LocalizationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LocalizationServiceApplication.class, args);
    }
}
