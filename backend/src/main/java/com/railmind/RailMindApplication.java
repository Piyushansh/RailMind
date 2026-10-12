package com.railmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RailMindApplication {
    public static void main(String[] args) {
        SpringApplication.run(RailMindApplication.class, args);
    }
}
