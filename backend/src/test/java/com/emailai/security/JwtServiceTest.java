package com.emailai.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios del servicio JWT (HMAC-SHA256).
 * No levanta contexto Spring: instanciación directa con clave de test.
 */
class JwtServiceTest {

    // HS256 necesita >= 32 bytes
    private static final String CLAVE = "clave-super-secreta-de-prueba-con-mas-de-32-bytes-para-hs256";

    private JwtService nuevo(int horas) {
        // envSecret no vacío → JwtService usa la clave sin tocar el sistema de ficheros
        return new JwtService(CLAVE, horas, "target/test-jwt");
    }

    @Test
    void generarYValidar_recuperaSubject() {
        JwtService jwt = nuevo(24);
        String token = jwt.generateToken("user@test.com");

        assertNotNull(token);
        assertEquals(3, token.split("\\.").length, "un JWT compact tiene 3 partes");
        assertTrue(jwt.isValid(token));
        assertEquals("user@test.com", jwt.extractSubject(token));
    }

    @Test
    void tokenModificado_noEsValido() {
        JwtService jwt = nuevo(24);
        String token = jwt.generateToken("user@test.com");
        String roto = token.substring(0, token.length() - 5) + "XXXXX";

        assertFalse(jwt.isValid(roto));
    }

    @Test
    void tokenDeOtraClave_noEsValido() {
        JwtService emisor = nuevo(24);
        JwtService otro = new JwtService(
                "otra-clave-totalmente-distinta-tambien-larga-para-hs256-0123456789",
                24, "target/test-jwt");
        String token = emisor.generateToken("user@test.com");

        assertTrue(emisor.isValid(token));
        assertFalse(otro.isValid(token), "un token firmado con otra clave no debe validar");
    }

    @Test
    void tokenExpirado_noEsValido() {
        // expirationHours negativo => fecha de expiración en el pasado
        JwtService jwt = nuevo(-1);
        String token = jwt.generateToken("user@test.com");

        assertFalse(jwt.isValid(token));
    }

    @Test
    void basuraONull_noEsValido() {
        JwtService jwt = nuevo(24);
        assertFalse(jwt.isValid("esto.no.es.un.jwt"));
        assertFalse(jwt.isValid(""));
        assertFalse(jwt.isValid("aaa.bbb.ccc"));
    }
}
