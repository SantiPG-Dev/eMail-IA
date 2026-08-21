package com.emailai.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.prefs.Preferences;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

// Almacén de configuración local en fichero properties.
// Portado del JavaFX original, los getOrMigrate* permiten importar
// ajustes de usuarios que tengan Preferences del Java legacy.
@Component
public class AppConfigStore {

    private static final Path CONFIG_DIR  = DataDir.config();
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("preferences.properties");

    private final Properties props = new Properties();

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (Files.exists(CONFIG_FILE)) {
                try (InputStream is = Files.newInputStream(CONFIG_FILE)) {
                    props.load(is);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudo inicializar AppConfigStore", e);
        }
    }

        public static Path getConfigFilePath() {
        return CONFIG_FILE.toAbsolutePath();
    }

    // Para tests: resetea y recarga del disco
    public void recargar() {
        props.clear();
        try {
            if (Files.exists(CONFIG_FILE)) {
                try (InputStream is = Files.newInputStream(CONFIG_FILE)) {
                    props.load(is);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudo recargar AppConfigStore", e);
        }
    }

    // ---------------------------------------------------------------
    // Lectura / escritura
    // ---------------------------------------------------------------

    public synchronized String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public synchronized void put(String key, String value) {
        props.setProperty(key, value);
        guardar();
    }

    public synchronized int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public synchronized void putInt(String key, int value) {
        props.setProperty(key, String.valueOf(value));
        guardar();
    }

    public synchronized long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public synchronized void putLong(String key, long value) {
        props.setProperty(key, String.valueOf(value));
        guardar();
    }

    public synchronized boolean getBoolean(String key, boolean defaultValue) {
        String v = props.getProperty(key);
        if (v == null) return defaultValue;
        return Boolean.parseBoolean(v);
    }

    public synchronized void putBoolean(String key, boolean value) {
        props.setProperty(key, String.valueOf(value));
        guardar();
    }

    public synchronized void remove(String key) {
        props.remove(key);
        guardar();
    }

    public synchronized boolean containsKey(String key) {
        return props.containsKey(key);
    }

    // ---------------------------------------------------------------
    // Persistencia
    // ---------------------------------------------------------------

    private void guardar() {
        try (OutputStream os = Files.newOutputStream(CONFIG_FILE)) {
            props.store(os, "eMail-IA Configuration");
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar configuración en " + CONFIG_FILE, e);
        }
    }

    // ---------------------------------------------------------------
    // Migración desde Preferences legacy (usuarios JavaFX existentes)
    // ---------------------------------------------------------------

    public synchronized String getOrMigrate(String key, String defaultValue, Preferences prefsLegacy) {
        String val = props.getProperty(key);
        if (val != null) return val;

        val = prefsLegacy.get(key, null);
        if (val != null) {
            props.setProperty(key, val);
            guardar();
            return val;
        }
        return defaultValue;
    }

    public synchronized int getOrMigrateInt(String key, int defaultValue, Preferences prefsLegacy) {
        String val = props.getProperty(key);
        if (val != null) {
            try { return Integer.parseInt(val); } catch (NumberFormatException e) { /* fall through */ }
        }
        int intVal = prefsLegacy.getInt(key, defaultValue);
        if (prefsLegacy.get(key, null) != null) {
            props.setProperty(key, String.valueOf(intVal));
            guardar();
        }
        return intVal;
    }

    public synchronized boolean getOrMigrateBoolean(String key, boolean defaultValue, Preferences prefsLegacy) {
        String val = props.getProperty(key);
        if (val != null) return Boolean.parseBoolean(val);
        boolean boolVal = prefsLegacy.getBoolean(key, defaultValue);
        if (prefsLegacy.get(key, null) != null) {
            props.setProperty(key, String.valueOf(boolVal));
            guardar();
        }
        return boolVal;
    }
}
