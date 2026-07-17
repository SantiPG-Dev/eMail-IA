package com.emailai.security;

import org.springframework.stereotype.Component;

// Sesión activa = usuario autenticado vía IMAP. Sin clave derivada (no hay contraseña maestra).
// La BD H2 ya está cifrada con cipher.key; las contraseñas IMAP viajan en texto plano dentro de H2.
@Component
public class SecureSessionManager {

    private transient boolean active = false;

    public void iniciarSesion() {
        this.active = true;
    }

    public void cerrarSesion() {
        active = false;
    }

    public boolean isActive() {
        return active;
    }
}
