package com.emailai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Credenciales en texto plano dentro de H2 (la BD ya está cifrada con cipher.key AES-256).
// En una app de escritorio local, la capa extra de cifrado es redundante.
@Service
public class CredentialService {

    private static final Logger log = LoggerFactory.getLogger(CredentialService.class);

    public CredentialService() {
    }

    public String cifrar(String textoPlano) {
        return textoPlano;
    }

    public String descifrar(String textoCifrado) {
        return textoCifrado;
    }
}
