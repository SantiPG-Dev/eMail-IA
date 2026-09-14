package com.emailai.config;

import java.nio.file.Path;

/**
 * Raíz de datos de la app: BD H2, cipher.key, modelos IA, weka-home y config.
 * Centraliza lo que antes estaba hardcodeado como Path.of("DB") / Path.of("config")
 * (relativo al cwd), que solo funcionaba cuando un wrapper controlaba el directorio.
 *
 * Resolución (en cada llamada — es barato, solo lee una property, y así los
 * tests pueden redirigirla sin depender del orden de carga de clases):
 * 1. System property emailai.data-dir — la fija EmailAiApplication.main desde
 *    el argumento --emailai.data-dir=... (lo pasa el wrapper Electron empaquetado).
 * 2. Env EMAILAI_DATA_DIR.
 * 3. "DB" relativo al cwd (desarrollo o instalación con wrapper que hace cd).
 */
public final class DataDir {

    private DataDir() {}

    private static Path resolveRoot() {
        String dir = System.getProperty("emailai.data-dir");
        if (dir == null || dir.isBlank()) dir = System.getenv("EMAILAI_DATA_DIR");
        if (dir == null || dir.isBlank()) dir = "DB";
        return Path.of(dir).toAbsolutePath().normalize();
    }

    /** Directorio raíz de datos (…/DB), absoluto y normalizado. */
    public static Path root() {
        return resolveRoot();
    }

    /** Ruta dentro de la raíz de datos: of("ia") → …/DB/ia. */
    public static Path of(String... children) {
        Path p = root();
        for (String c : children) p = p.resolve(c);
        return p;
    }

    /**
     * Directorio hermano de la raíz (si la raíz es ~/.eMailAI/DB → ~/.eMailAI).
     * Con el default relativo equivale al cwd, como en el layout de siempre.
     */
    public static Path base() {
        Path parent = root().getParent();
        return parent != null ? parent : root();
    }

    /** Directorio de configuración (config/, hermano de DB/). */
    public static Path config(String... children) {
        Path p = base().resolve("config");
        for (String c : children) p = p.resolve(c);
        return p;
    }
}
