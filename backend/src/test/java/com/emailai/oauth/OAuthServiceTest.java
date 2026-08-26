package com.emailai.oauth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios de OAuthService — lógica pura SIN red.
 * Cubre el routing de proveedor, la generación de state y el contrato
 * anti-CSRF/anti-replay (el paquete oauth estaba al 5.5% de cobertura).
 *
 * El canje HTTP real de tokens queda cubierto por el timeout configurado
 * (restClientConTimeout) y por OAuthControllerTest (servicio mockeado);
 * aquí no se abre puerto ni se llama a Google/Microsoft.
 */
class OAuthServiceTest {

    private OAuthService service;

    @BeforeEach
    void setUp() {
        OAuthService.ACTIVE_OAUTH_STATES.clear();
        service = new OAuthService(
                "test-google-id", "test-google-secret",
                "test-ms-id", "test-ms-secret");
    }

    @AfterEach
    void limpiarStates() {
        OAuthService.ACTIVE_OAUTH_STATES.clear();
    }

    // ── Routing de proveedor ────────────────────────────────────

    @Test
    void iniciarFlujo_google_devuelveUrlDeAutorizacionCorrecta() {
        String url = service.iniciarFlujo("google");

        assertNotNull(url);
        assertTrue(url.startsWith(GoogleOAuthProvider.AUTH_URL),
                "La URL debe empezar por el endpoint de Google");
        assertTrue(url.contains("client_id=test-google-id"));
        assertTrue(url.contains("redirect_uri=http://localhost:9876/oauth/callback"));
        assertTrue(url.contains("state="));
    }

    @Test
    void iniciarFlujo_microsoft_caseInsensitive_devuelveUrlMicrosoft() {
        // El switch usa toUpperCase() — "microsoft" y "MICROSOFT" deben funcionar igual
        String urlMin = service.iniciarFlujo("microsoft");
        String urlMay = service.iniciarFlujo("MICROSOFT");

        for (String url : new String[]{urlMin, urlMay}) {
            assertTrue(url.startsWith(MicrosoftOAuthProvider.AUTH_URL),
                    "La URL debe empezar por el endpoint de Microsoft");
            assertTrue(url.contains("client_id=test-ms-id"));
        }
    }

    @Test
    void iniciarFlujo_proveedorNoSoportado_lanzaIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.iniciarFlujo("dropbox"));
        assertTrue(ex.getMessage().contains("dropbox"));
    }

    // ── State anti-CSRF / anti-replay ───────────────────────────

    @Test
    void generarState_registraStateConsumibleUnaVez() {
        String state = service.generarState();

        assertNotNull(state);
        assertFalse(state.isBlank());
        // URL-safe (Base64 url sin padding)
        assertTrue(state.matches("^[A-Za-z0-9_-]+$"));

        // Contrato anti-replay: el state está activo y se consume una sola vez
        assertTrue(OAuthService.ACTIVE_OAUTH_STATES.containsKey(state),
                "generarState debe registrar el state como activo");
        assertEquals(Boolean.TRUE, OAuthService.ACTIVE_OAUTH_STATES.remove(state),
                "Primer consumo: debe devolver TRUE (state válido)");
        assertNull(OAuthService.ACTIVE_OAUTH_STATES.remove(state),
                "Segundo consumo: debe devolver NULL (replay bloqueado)");
    }

    @Test
    void generarState_cadaLlamadaEsDistinta() {
        String s1 = service.generarState();
        String s2 = service.generarState();
        String s3 = service.generarState();

        assertNotEquals(s1, s2);
        assertNotEquals(s2, s3);
        assertNotEquals(s1, s3);
        assertEquals(3, OAuthService.ACTIVE_OAUTH_STATES.size());
    }

    @Test
    void iniciarFlujo_registraStateParaCsrf() {
        // iniciarFlujo llama a generarState internamente → el state queda registrado
        // para que esperarCallback pueda validarlo al volver del navegador.
        int antes = OAuthService.ACTIVE_OAUTH_STATES.size();
        service.iniciarFlujo("google");
        assertEquals(antes + 1, OAuthService.ACTIVE_OAUTH_STATES.size(),
                "iniciarFlujo debe registrar exactamente un state nuevo");
    }

    // ── formBody: encoding de application/x-www-form-urlencoded ──

    @Test
    void formBody_codificaCaracteresEspecialesDelSecret() {
        // Auditoría 2026-08-26: concatenar a mano rompía el body si el
        // clientSecret contenía &, = o +
        String body = OAuthService.formBody(
                "grant_type", "authorization_code",
                "client_secret", "abc&def=ghi+jkl espacio");

        assertEquals("grant_type=authorization_code&client_secret=abc%26def%3Dghi%2Bjkl+espacio",
                body, "&, = y + deben ir URL-encodeados");
    }

    @Test
    void formBody_valoresSimplesQuedanIguales() {
        assertEquals("a=1&b=hola",
                OAuthService.formBody("a", "1", "b", "hola"));
    }

    @Test
    void formBody_urlDeRedirectSeCodificaComoValor() {
        String body = OAuthService.formBody("redirect_uri", "http://localhost:9876/oauth/callback");
        assertEquals("redirect_uri=http%3A%2F%2Flocalhost%3A9876%2Foauth%2Fcallback",
                body, "el valor completo se forma-encodea (el proveedor lo decodifica)");
    }

    @Test
    void formBody_numImparDeArgumentosLanza() {
        assertThrows(IllegalArgumentException.class, () -> OAuthService.formBody("solo"));
    }
}
