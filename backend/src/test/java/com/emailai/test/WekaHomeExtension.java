package com.emailai.test;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import weka.core.Environment;

/**
 * Extensión JUnit 5 auto-registrada via {@code META-INF/services} que inyecta
 * {@code WEKA_HOME} en el Environment de Weka antes de que cualquier test
 * cargue clases de Weka. Así {@code ~/wekafiles} no se crea en el home del
 * usuario y Weka escribe en DB/weka-home, igual que en la app.
 *
 * <p>Se activa automáticamente para todos los tests sin necesidad de anotar
 * cada clase de test con {@code @ExtendWith}.</p>
 */
public class WekaHomeExtension implements BeforeAllCallback {

    private static volatile boolean ejecutado = false;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (ejecutado) return;
        ejecutado = true;

        try {
            Path wekaHome = Path.of("DB", "weka-home").toAbsolutePath();
            Files.createDirectories(wekaHome);
            Environment env = Environment.getSystemWide();
            env.addVariableSystemWide("WEKA_HOME", wekaHome.toString());
        } catch (IOException e) {
            throw new RuntimeException(
                "No se pudo inicializar WEKA_HOME para los tests", e);
        }
    }
}
