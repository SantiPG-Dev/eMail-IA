package com.emailai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EmailAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailAiApplication.class, args);
    }
}
