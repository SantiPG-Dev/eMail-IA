package com.emailai.config;

import java.nio.file.Path;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configura el DataSource de H2 con cifrado AES.
 *
 * <p>H2 con {@code CIPHER=AES} requiere una contraseña compuesta:
 * {@code "<filePassword> <userPassword>"}. El userPassword es vacío (usuario "sa"),
 * por lo que la contraseña JDBC resulta en {@code "<filePassword> sa"}.
 *
 * <p>La {@code filePassword} se lee de {@code DB/cipher.key} vía
 * {@link DatabaseKeyStore}, garantizando compatibilidad con el legacy JavaFX.
 *
 * <p><b>Decisión arquitectónica D1:</b> las 4 BDs H2 del legacy (correos, agenda,
 * contactos, ia) se consolidan en una sola ({@code DB/emailai}) para simplificar
 * Spring Data JPA. Los datos siguen siendo locales y cifrados.
 */
@Configuration
public class DatabaseConfig {

    private static final String DB_PARAMS = ";CIPHER=AES;DATABASE_TO_LOWER=TRUE";

    @Bean
    public DataSource dataSource() {
        String filePassword = DatabaseKeyStore.getFilePassword();
        // H2 requiere ruta absoluta o con prefijo ./ (no relativa implícita)
        String dbPath = Path.of("DB", "emailai").toAbsolutePath().toString().replace("\\", "/");
        String url = "jdbc:h2:file:" + dbPath + DB_PARAMS;
        // H2 CIPHER: la contraseña es "filePassword userPassword" (userPassword vacío → "sa")
        String password = filePassword + " sa";

        return DataSourceBuilder.create()
                .url(url)
                .username("sa")
                .password(password)
                .driverClassName("org.h2.Driver")
                .build();
    }
}
