package com.lizaveta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class RemoteBackupApplication {

    public static void main(String[] args) {
        SpringApplication.run(RemoteBackupApplication.class, args);
    }

}