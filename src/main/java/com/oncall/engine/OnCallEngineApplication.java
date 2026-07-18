package com.oncall.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OnCallEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnCallEngineApplication.class, args);
    }
}
