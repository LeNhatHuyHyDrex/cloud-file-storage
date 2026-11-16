package com.cloudfs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CloudFileStorageApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudFileStorageApplication.class, args);
    }
}
