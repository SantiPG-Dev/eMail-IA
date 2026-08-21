package com.emailai.config;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

// DataSource H2 con cifrado AES. La contraseña JDBC se compone de la clave de
// archivo (de cipher.key) + " sa" (userPassword vacío). Esto mantiene compatibilidad
// con el legacy JavaFX que usaba el mismo sistema.
// Decisión: las 4 BDs del legacy (correos, agenda, contactos, ia) se consolidan
// en una sola (DB/emailai) para simplificar Spring Data JPA.
@Configuration
@Profile("!test")
public class DatabaseConfig {

    private static final String DB_PARAMS = ";CIPHER=AES;DATABASE_TO_LOWER=TRUE";

    @Bean
    public DataSource dataSource() {
        String filePassword = DatabaseKeyStore.getFilePassword();
        String dbPath = DataDir.of("emailai").toString().replace("\\", "/");
        String url = "jdbc:h2:file:" + dbPath + DB_PARAMS;
        // H2 CIPHER: "filePassword userPassword" → userPassword vacío es "sa"
        String password = filePassword + " sa";

        return DataSourceBuilder.create()
                .url(url)
                .username("sa")
                .password(password)
                .driverClassName("org.h2.Driver")
                .build();
    }
}
