package com.emailai.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests de LoginRateLimiter (bug crítico #8: login sin límite martilleaba
 * el servidor IMAP con conexiones reales).
 */
class LoginRateLimiterTest {

    private final LoginRateLimiter limiter = new LoginRateLimiter();

    private boolean intento(String email) {
        return limiter.permitir(email, new StringBuilder());
    }

    @Test
    void primerosIntentosPermitidos() {
        for (int i = 0; i < LoginRateLimiter.INTENTOS; i++) {
            assertTrue(intento("user@mail.com"), "intento " + (i + 1) + " debe pasar");
        }
    }

    @Test
    void excederIntentos_bloqueaConMotivo() {
        for (int i = 0; i < LoginRateLimiter.INTENTOS; i++) {
            assertTrue(intento("user@mail.com"));
        }
        StringBuilder motivo = new StringBuilder();
        boolean permitido = limiter.permitir("user@mail.com", motivo);

        assertFalse(permitido);
        assertTrue(motivo.toString().contains("bloqueado"),
                "el motivo debe indicar bloqueo: " + motivo);
    }

    @Test
    void bloqueoPersisteEnIntentosSiguientes() {
        for (int i = 0; i <= LoginRateLimiter.INTENTOS; i++) {
            intento("user@mail.com");
        }
        // Siguientes intentos: siguen bloqueados sin consumir nada
        for (int i = 0; i < 3; i++) {
            assertFalse(intento("user@mail.com"));
        }
    }

    @Test
    void emailsIndependientes() {
        for (int i = 0; i <= LoginRateLimiter.INTENTOS; i++) {
            intento("a@mail.com");
        }
        assertFalse(intento("a@mail.com"));
        // Otro email no hereda el bloqueo
        assertTrue(intento("b@mail.com"));
    }

    @Test
    void resetTrasLoginCorrecto() {
        for (int i = 0; i < LoginRateLimiter.INTENTOS; i++) {
            assertTrue(intento("user@mail.com"));
        }
        limiter.reset("user@mail.com");
        // Tras reset, el usuario puede volver a intentar
        assertTrue(intento("user@mail.com"));
    }

    @Test
    void limiteGlobal() {
        // Distintos emails agotan el límite global sin llegar al individual
        int total = 0;
        StringBuilder motivo = new StringBuilder();
        for (int e = 0; e < LoginRateLimiter.GLOBAL_MINUTO + 1; e++) {
            if (limiter.permitir("user" + e + "@mail.com", motivo)) {
                total++;
            }
        }
        assertEquals(LoginRateLimiter.GLOBAL_MINUTO, total,
                "el global debe cortar exactamente en " + LoginRateLimiter.GLOBAL_MINUTO);
        assertTrue(motivo.toString().contains("en total"));
    }
}
