package com.emailai.security;

import java.util.Arrays;

import org.springframework.stereotype.Component;

// Mantiene en memoria la clave derivada (PBKDF2) que SecureStorage usa
// para cifrar/descifrar. Al cerrar sesión se sobreescribe con ceros.
@Component
public class SecureSessionManager {

    private transient String derivedKey;
    private transient boolean active = false;

    public void iniciarSesion(String derivedKey) {
        this.derivedKey = derivedKey;
        this.active = true;
    }

    public String getDerivedKey() {
        if (!active || derivedKey == null) {
            throw new IllegalStateException("Sesión no iniciada");
        }
        return derivedKey;
    }

    // Limpieza explícita de memoria al cerrar sesión
    public void cerrarSesion() {
        if (derivedKey != null) {
            char[] chars = derivedKey.toCharArray();
            Arrays.fill(chars, '\0');
        }
        derivedKey = null;
        active = false;
    }

    public boolean isActive() {
        return active;
    }
}
