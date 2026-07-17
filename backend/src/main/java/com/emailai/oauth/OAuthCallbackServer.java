package com.emailai.oauth;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

// Servidor HTTP mínimo (JDK built-in) para capturar el callback OAuth2.
// Portado del JavaFX legacy. Se inicia, espera el código, se detiene.
public class OAuthCallbackServer {

    private final HttpServer server;
    private final CompletableFuture<OAuthCallbackResult> callbackFuture = new CompletableFuture<>();

    public OAuthCallbackServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/oauth/callback", new CallbackHandler());
    }

    public void start() {
        server.setExecutor(null);
        Thread thread = new Thread(server::start, "oauth-callback-server");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        server.stop(0);
    }

    public OAuthCallbackResult waitForCallback(int timeoutSeconds)
            throws TimeoutException, InterruptedException, ExecutionException {
        try {
            return callbackFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        } finally {
            server.stop(0);
        }
    }

    public record OAuthCallbackResult(String code, String state) {}

    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String responseHtml;

            try {
                if (query != null && query.contains("code=")) {
                    String code = extractParam(query, "code");
                    String state = extractParam(query, "state");
                    callbackFuture.complete(new OAuthCallbackResult(code, state));
                    responseHtml = successPage("Autenticación correcta. Puedes cerrar esta pestaña.");
                } else if (query != null && query.contains("error=")) {
                    String error = extractParam(query, "error");
                    callbackFuture.completeExceptionally(new OAuth2Exception("OAuth error: " + error));
                    responseHtml = errorPage("Error de autenticación: " + error);
                } else {
                    callbackFuture.completeExceptionally(new OAuth2Exception("No authorization code in callback"));
                    responseHtml = errorPage("No se recibió código de autorización.");
                }
            } catch (Exception e) {
                callbackFuture.completeExceptionally(e);
                responseHtml = errorPage("Error interno: " + e.getMessage());
            }

            byte[] bytes = responseHtml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            new Thread(() -> server.stop(0)).start();
        }

        private String extractParam(String query, String param) {
            String prefix = param + "=";
            int idx = query.indexOf(prefix);
            if (idx == -1) return "";
            int start = idx + prefix.length();
            int end = query.indexOf("&", start);
            if (end == -1) end = query.length();
            return URLDecoder.decode(query.substring(start, end), StandardCharsets.UTF_8);
        }

        private String escapeHtml(String text) {
            if (text == null) return "";
            return text.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;").replace("\"", "&quot;");
        }

        private String successPage(String message) {
            return "<!DOCTYPE html><html><body style='display:flex;justify-content:center;align-items:center;"
                 + "height:100vh;font-family:system-ui;background:#f0f0f0'><div style='text-align:center;"
                 + "padding:40px;background:white;border-radius:12px'><h2 style='color:#2e7d32'>✓ "
                 + escapeHtml(message) + "</h2><p>Puedes cerrar esta pestaña.</p></div></body></html>";
        }

        private String errorPage(String message) {
            return "<!DOCTYPE html><html><body style='display:flex;justify-content:center;align-items:center;"
                 + "height:100vh;font-family:system-ui;background:#f0f0f0'><div style='text-align:center;"
                 + "padding:40px;background:white;border-radius:12px'><h2 style='color:#c62828'>✗ "
                 + escapeHtml(message) + "</h2><p>Puedes cerrar esta pestaña.</p></div></body></html>";
        }
    }
}
