package com.emailai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del backend REST de eMail-IA.
 *
 * <p>Fase 1 de la migración a Spring Boot + React + Electron:
 * backend base con H2 cifrada, Flyway, y endpoint de health.
 */
@SpringBootApplication
public class EmailAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailAiApplication.class, args);
    }
}
