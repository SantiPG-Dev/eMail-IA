package com.emailai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifica que el contexto de Spring Boot arranca correctamente:
 * DataSource H2 cifrada, Flyway migration, beans de configuración.
 */
@SpringBootTest
class EmailAiApplicationTest {

    @Test
    void contextLoads() {
        // Si el contexto arranca, la configuración es válida:
        // - DatabaseKeyStore lee/crea cipher.key
        // - DatabaseConfig crea el DataSource H2 cifrado
        // - Flyway ejecuta V1__init.sql
        // - AppConfigStore se inicializa
    }
}
