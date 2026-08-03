package com.emailai.oauth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del servidor de callback OAuth2 en localhost puro (sin red externa).
 * Es exactamente el flujo real: Google/Microsoft redirige el navegador a
 * http://localhost:PUERTO/oauth/callback — aquí lo simulamos con HttpClient del JDK.
 *
 * Cubre el parseo de code/state, el path de error y el timeout (OAuthCallbackServer
 * era la clase más grande del paquete oauth y estaba a 0%).
 */
class OAuthCallbackServerTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    // Puerto efímero: reservar y liberar antes de crear el servidor. Ventana de
    // carrera mínima, aceptable para test. no tocar prod para exponer getAddress.
    private static int puertoLibre() throws Exception {
        try (var s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static HttpResponse<String> get(int port, String query) throws Exception {
        return HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/oauth/callback?" + query))
                .GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void callback_conCodeYState_devuelveResultadoYHtmlOk() throws Exception {
        int port = puertoLibre();
        var server = new OAuthCallbackServer(port);
        server.start();
        try {
            // code con carácter URL-encoded (+ -> espacio) para verificar el decode
            HttpResponse<String> resp = get(port, "code=4%2F0Ax4test&state=abc123");

            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().contains("Autenticación correcta"));

            OAuthCallbackServer.OAuthCallbackResult result = server.waitForCallback(5);
            assertEquals("4/0Ax4test", result.code(), "El code debe ir URL-decoded");
            assertEquals("abc123", result.state());
        } finally {
            server.stop();
        }
    }

    @Test
    void callback_conError_completaConExcepcion() throws Exception {
        int port = puertoLibre();
        var server = new OAuthCallbackServer(port);
        server.start();
        try {
            HttpResponse<String> resp = get(port, "error=access_denied&state=xyz");

            assertEquals(200, resp.statusCode());   // el handler SIEMPRE responde 200 + HTML
            assertTrue(resp.body().contains("access_denied"));

            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> server.waitForCallback(5));
            // El callback completó excepcionalmente con OAuth2Exception envuelto
            assertInstanceOf(OAuth2Exception.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("access_denied"));
        } finally {
            server.stop();
        }
    }

    @Test
    void callback_sinCodeNiError_completaConExcepcion() throws Exception {
        int port = puertoLibre();
        var server = new OAuthCallbackServer(port);
        server.start();
        try {
            get(port, "foo=bar&sin=code");

            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> server.waitForCallback(5));
            assertInstanceOf(OAuth2Exception.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().toLowerCase().contains("código")
                    || ex.getCause().getMessage().toLowerCase().contains("authorization code"));
        } finally {
            server.stop();
        }
    }

    @Test
    void waitForCallback_sinCallback_lanzaTimeout() throws Exception {
        int port = puertoLibre();
        var server = new OAuthCallbackServer(port);
        server.start();
        try {
            // Nadie llama al callback → debe caducar rápido
            assertThrows(TimeoutException.class, () -> server.waitForCallback(1));
        } finally {
            server.stop();
        }
    }
}
