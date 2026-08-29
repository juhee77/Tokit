package com.tokit;
 
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
 
@EnableRetry
@SpringBootApplication
@EnableScheduling
public class TokitApplication {
    public static void main(String[] args) {
        SpringApplication.run(TokitApplication.class, args);
    }
}

