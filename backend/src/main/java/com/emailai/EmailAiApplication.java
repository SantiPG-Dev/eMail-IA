package com.emailai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import weka.core.Environment;

// Clase principal de la app.
// @EnableScheduling activa los cron del SyncSchedulerService y demás tareas programadas.
@SpringBootApplication
@EnableScheduling
public class EmailAiApplication {

    public static void main(String[] args) {
        fijarDataDir(args);
        redirigirWekaHome();
        SpringApplication.run(EmailAiApplication.class, args);
    }

    /**
     * Copia --emailai.data-dir=... a System property antes de arrancar nada,
     * para que DataDir (estático, sin Spring) resuelva la misma ruta que la
     * propiedad de Spring. Sin esto, el backend empaquetado escribiría en el
     * cwd del lanzador (AppImage) en vez de en el data-dir del wrapper.
     */
    private static void fijarDataDir(String[] args) {
        for (String a : args) {
            if (a.startsWith("--emailai.data-dir=")) {
                System.setProperty("emailai.data-dir", a.substring("--emailai.data-dir=".length()));
            }
        }
    }

    /**
     * Weka por defecto crea ~/wekafiles en el home del usuario. Se inyecta
     * WEKA_HOME en el Environment de Weka (que WekaPackageManager lee) para que
     * escriba en DB/weka-home, dentro del proyecto/instalación como el resto
     * del flujo (H2, modelos, jwt.key). Debe ejecutarse antes de que cualquier
     * clase de Weka se cargue, por eso va al inicio de main().
     */
    private static void redirigirWekaHome() {
        try {
            Path wekaHome = com.emailai.config.DataDir.of("weka-home");
            Files.createDirectories(wekaHome);
            Environment env = Environment.getSystemWide();
            env.addVariableSystemWide("WEKA_HOME", wekaHome.toString());
        } catch (IOException e) {
            throw new RuntimeException("No se pudo inicializar DB/weka-home", e);
        }
    }
}
