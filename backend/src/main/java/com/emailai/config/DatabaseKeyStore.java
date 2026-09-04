package com.emailai.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

// Gestiona la clave de cifrado de H2 (cipher.key).
// 16 bytes aleatorios generados con SecureRandom.
// Compatible con el legacy JavaFX: mismo formato, misma ubicación (DB/cipher.key).
// Si el archivo no existe, se genera una nueva clave automáticamente.
public final class DatabaseKeyStore {

    // Ruta resuelta bajo demanda: si se cachea al cargar la clase, los tests no
    // pueden redirigir DataDir después (orden de clases impredecible en surefire).
    private static Path keyFile() {
        return DataDir.of("cipher.key");
    }
    private static final SecureRandom RANDOM = new SecureRandom();

    private static String cachedPassword;

    private DatabaseKeyStore() {}

    // Devuelve la clave en Base64. Cacheada tras la primera lectura para evitar
    // acceso a disco en cada petición que necesita DataSource.
    public static synchronized String getFilePassword() {
        if (cachedPassword != null) return cachedPassword;

        try {
            Files.createDirectories(DataDir.root());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el directorio DB: " + e.getMessage(), e);
        }

        if (Files.exists(keyFile())) {
            try {
                byte[] raw = Files.readAllBytes(keyFile());
                cachedPassword = Base64.getEncoder().encodeToString(raw);
                return cachedPassword;
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo leer cipher.key: " + e.getMessage(), e);
            }
        }

        // Primera ejecución: generar cipher.key nuevo
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        try {
            Files.write(keyFile(), raw);
            // Permisos restrictivos (owner-only): cipher.key cifra la BD entera
            // y de él deriva la clave de las credenciales IMAP/OAuth guardadas.
            try {
                Files.setPosixFilePermissions(keyFile(), java.util.Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // Windows no soporta POSIX perms
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear cipher.key: " + e.getMessage(), e);
        }

        // Endurecer también una clave preexistente creada con perms abiertos
        try {
            if (Files.exists(keyFile())) {
                Files.setPosixFilePermissions(keyFile(), java.util.Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            }
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows o FS sin POSIX: no crítico
        }

        // Si la BD se regeneró (cipher.key nuevo), el config stale (con el hash
        // de contraseña maestra anterior) ya no sirve — borrarlo para forzar setup.
        Path staleConfig = DataDir.config("preferences.properties");
        try {
            if (Files.exists(staleConfig)) {
                Files.delete(staleConfig);
                System.out.println("[DatabaseKeyStore] cipher.key nuevo: config stale borrado");
            }
        } catch (IOException ignored) {
            // Si no se puede borrar, no es crítico
        }

        cachedPassword = Base64.getEncoder().encodeToString(raw);
        return cachedPassword;
    }

    // Para backup/restore manual
    public static Path getKeyFilePath() {
        return keyFile().toAbsolutePath();
    }
}
