package com.emailai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Cifra/descifra credenciales con SecureStorage (AES-256-GCM + PBKDF2).
// Al guardar cuenta → cifra. Al conectar IMAP → descifra.
@Service
public class CredentialService {

    private static final Logger log = LoggerFactory.getLogger(CredentialService.class);

    private final SecureSessionManager sessionManager;

    public CredentialService(SecureSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Cifra una contraseña en texto plano usando la clave de sesión.
     */
    public String cifrar(String textoPlano) {
        if (textoPlano == null || textoPlano.isBlank()) {
            return textoPlano;
        }
        try {
            SecureStorage storage = new SecureStorage(sessionManager.getDerivedKey());
            return storage.cifrar(textoPlano);
        } catch (Exception e) {
            log.error("Error al cifrar credencial: {}", e.getMessage());
            return textoPlano; // fallback: guardar en texto plano
        }
    }

    /**
     * Descifra una contraseña usando la clave de sesión.
     * Si el texto no está cifrado (formato antiguo), lo devuelve tal cual.
     */
    public String descifrar(String textoCifrado) {
        if (textoCifrado == null || textoCifrado.isBlank()) {
            return textoCifrado;
        }
        try {
            SecureStorage storage = new SecureStorage(sessionManager.getDerivedKey());
            return storage.descifrar(textoCifrado);
        } catch (Exception e) {
            // Si falla el descifrado, asumir que es texto plano (migración desde versión anterior)
            log.debug("Credencial no cifrada (formato antiguo): {}", e.getMessage());
            return textoCifrado;
        }
    }
}
