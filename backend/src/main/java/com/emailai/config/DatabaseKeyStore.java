package com.emailai.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Gestiona la clave de cifrado de la base de datos H2 ({@code cipher.key}).
 *
 * <p>El archivo {@code DB/cipher.key} contiene 16 bytes aleatorios que se usan
 * como contraseña de archivo para el cifrado AES de H2 ({@code CIPHER=AES}).
 * Si el archivo no existe, se genera una nueva clave y se persiste.
 *
 * <p>Es compatible con el {@code cipher.key} del legacy JavaFX: mismo formato
 * (16 bytes raw), misma ubicación ({@code DB/cipher.key}). Esto permite que
 * usuarios existentes reutilicen su clave al migrar los datos (Fase 10).
 */
public final class DatabaseKeyStore {

    private static final Path DB_DIR = Path.of("DB");
    private static final Path KEY_FILE = DB_DIR.resolve("cipher.key");
    private static final SecureRandom RANDOM = new SecureRandom();

    private static String cachedPassword;

    private DatabaseKeyStore() {}

    /**
     * Devuelve la contraseña de archivo de H2 (Base64 de los 16 bytes de cipher.key).
     * La crea si no existe. El resultado se cachea tras la primera llamada.
     */
    public static synchronized String getFilePassword() {
        if (cachedPassword != null) return cachedPassword;

        try {
            Files.createDirectories(DB_DIR);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el directorio DB: " + e.getMessage(), e);
        }

        if (Files.exists(KEY_FILE)) {
            try {
                byte[] raw = Files.readAllBytes(KEY_FILE);
                cachedPassword = Base64.getEncoder().encodeToString(raw);
                return cachedPassword;
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo leer cipher.key: " + e.getMessage(), e);
            }
        }

        // Generar nueva clave y persistirla
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        try {
            Files.write(KEY_FILE, raw);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear cipher.key: " + e.getMessage(), e);
        }
        cachedPassword = Base64.getEncoder().encodeToString(raw);
        return cachedPassword;
    }

    /**
     * Ruta absoluta del archivo cipher.key (para backup/restore, Fase 4).
     */
    public static Path getKeyFilePath() {
        return KEY_FILE.toAbsolutePath();
    }
}
