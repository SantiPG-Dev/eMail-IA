package com.emailai.security;

import java.util.Arrays;

import org.springframework.stereotype.Component;

/**
 * Gestiona la sesión del usuario tras la autenticación con contraseña maestra.
 *
 * <p>Mantiene en memoria la clave derivada (PBKDF2) que SecureStorage usa
 * para cifrar/descifrar credenciales almacenadas (OAuth tokens, passwords IMAP).
 * La clave se limpia al cerrar sesión.
 */
@Component
public class SecureSessionManager {

    private transient String derivedKey;
    private transient boolean active = false;

    /**
     * Inicia sesión: almacena la clave derivada.
     */
    public void iniciarSesion(String derivedKey) {
        this.derivedKey = derivedKey;
        this.active = true;
    }

    /**
     * Devuelve la clave derivada actual.
     */
    public String getDerivedKey() {
        if (!active || derivedKey == null) {
            throw new IllegalStateException("Sesión no iniciada");
        }
        return derivedKey;
    }

    /**
     * Cierra sesión y limpia la clave de memoria.
     */
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
