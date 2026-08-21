package com.emailai.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Publica el "ready file" con el puerto real del backend cuando Tomcat está
 * listo. Necesario porque la app arranca con --server.port=0 (puerto efímero
 * asignado por el SO): el wrapper Electron no puede conocer el puerto de otra
 * forma y no debe escanear puertos fijos.
 *
 * Se activa con la propiedad emailai.ready-file (--emailai.ready-file=... o
 * env EMAILAI_READYFILE), que pasa el wrapper. Sin ella no escribe nada
 * (mvn spring-boot:run manual, tests...).
 *
 * Contenido: {"port":8123,"pid":4242} — el pid permite al wrapper detectar
 * stale ready files de backends que murieron sin cleanup (kill -9).
 * Se borra en shutdown limpio (@PreDestroy).
 */
@Component
public class ReadyFileWriter {

    private static final Logger log = LoggerFactory.getLogger(ReadyFileWriter.class);

    private final Environment env;

    public ReadyFileWriter(Environment env) {
        this.env = env;
    }

    @EventListener
    public void onWebServerInitialized(WebServerInitializedEvent event) {
        writeReady(event.getWebServer().getPort());
    }

    void writeReady(int port) {
        String file = resolveReadyFile();
        if (file == null || file.isBlank()) return;

        Path path = Path.of(file);
        long pid = ProcessHandle.current().pid();
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, "{\"port\":" + port + ",\"pid\":" + pid + "}\n");
            try {
                Files.setPosixFilePermissions(path, Set.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // Windows
            }
            log.info("Backend listo en puerto {}: ready file {}", port, path.toAbsolutePath());
        } catch (IOException e) {
            log.warn("No se pudo escribir el ready file {}: {}", path.toAbsolutePath(), e.getMessage());
        }
    }

    @PreDestroy
    void deleteReadyFile() {
        String file = resolveReadyFile();
        if (file == null || file.isBlank()) return;
        try {
            Files.deleteIfExists(Path.of(file));
        } catch (IOException e) {
            log.warn("No se pudo borrar el ready file {}: {}", file, e.getMessage());
        }
    }

    private String resolveReadyFile() {
        String p = env.getProperty("emailai.ready-file");
        if (p == null || p.isBlank()) p = System.getenv("EMAILAI_READYFILE");
        return p;
    }
}
