package com.emailai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Clase principal de la app.
// @EnableScheduling activa los cron del SyncSchedulerService y demás tareas programadas.
@SpringBootApplication
@EnableScheduling
public class EmailAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailAiApplication.class, args);
    }
}
