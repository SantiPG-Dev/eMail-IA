package com.emailai.oauth;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

// Orquesta el flujo OAuth2 completo: URL de auth → callback local → tokens.
// Las credenciales (clientId, clientSecret) se inyectan desde application.yml
// para que no viajen por la red ni se expongan al frontend.
@Service
public class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CALLBACK_PORT = 9876;

    /** Mapa de states activos (CSRF + anti-replay). */
    public static final ConcurrentHashMap<String, Boolean> ACTIVE_OAUTH_STATES = new ConcurrentHashMap<>();

    /** Estados de un flujo OAuth asíncrono. */
    public static final String FLUJO_PENDIENTE = "PENDIENTE";
    public static final String FLUJO_COMPLETADO = "COMPLETADO";
    public static final String FLUJO_TIMEOUT = "TIMEOUT";
    public static final String FLUJO_ERROR = "ERROR";

    /** Máximo de flujos terminados retenidos en memoria. */
    private static final int MAX_FLUJOS_RETENIDOS = 10;

    /** Flujos OAuth en curso o recién terminados, indexados por id. */
    private final ConcurrentHashMap<String, EstadoFlujo> flujos = new ConcurrentHashMap<>();

    /** Id del flujo con el servidor de callback escuchando (puerto único). */
    private volatile String flujoActivoId = null;

    private final ObjectMapper mapper;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String microsoftClientId;
    private final String microsoftClientSecret;

    public OAuthService(
            @Value("${emailai.oauth.google.client-id:}") String googleClientId,
            @Value("${emailai.oauth.google.client-secret:}") String googleClientSecret,
            @Value("${emailai.oauth.microsoft.client-id:}") String microsoftClientId,
            @Value("${emailai.oauth.microsoft.client-secret:}") String microsoftClientSecret) {
        this.mapper = new ObjectMapper();
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.microsoftClientId = microsoftClientId;
        this.microsoftClientSecret = microsoftClientSecret;
    }

    /**
     * Genera un state aleatorio para CSRF.
     */
    public String generarState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ACTIVE_OAUTH_STATES.put(state, Boolean.TRUE);
        return state;
    }

    // ── Iniciar flujo ───────────────────────────────────────────

    /**
     * Inicia flujo OAuth con Google.
     * @return URL de autorización para abrir en el navegador
     */
    public String iniciarFlujoGoogle() {
        String state = generarState();
        GoogleOAuthProvider provider = new GoogleOAuthProvider(
                googleClientId, googleClientSecret,
                "http://localhost:" + CALLBACK_PORT + "/oauth/callback");
        return provider.generarUrlAutorizacion(state);
    }

    /**
     * Inicia flujo OAuth con Microsoft.
     */
    public String iniciarFlujoMicrosoft() {
        String state = generarState();
        MicrosoftOAuthProvider provider = new MicrosoftOAuthProvider(
                microsoftClientId, microsoftClientSecret,
                "http://localhost:" + CALLBACK_PORT + "/oauth/callback");
        return provider.generarUrlAutorizacion(state);
    }

    /**
     * Inicia el flujo OAuth según el proveedor.
     */
    public String iniciarFlujo(String proveedor) {
        return switch (proveedor.toUpperCase()) {
            case "GOOGLE" -> iniciarFlujoGoogle();
            case "MICROSOFT" -> iniciarFlujoMicrosoft();
            default -> throw new IllegalArgumentException("Proveedor OAuth no soportado: " + proveedor);
        };
    }

    // ── Flujo asíncrono (no bloquea hilos Tomcat) ───────────────

    /** Flujo iniciado: id para consultar estado + URL de autorización. */
    public record FlujoIniciado(String flujoId, String authUrl) {}

    /** Estado de un flujo: PENDIENTE | COMPLETADO | TIMEOUT | ERROR. */
    public record EstadoFlujo(String estado, OAuthSession session, String error) {}

    /**
     * Inicia el flujo OAuth de forma asíncrona: arranca el servidor de
     * callback en un hilo daemon y devuelve inmediatamente el id del flujo
     * y la URL de autorización. El resultado se consulta con estadoFlujo().
     *
     * Solo puede haber un flujo escuchando a la vez (el callback usa un
     * puerto fijo): si ya hay uno PENDIENTE lanza IllegalStateException.
     */
    public synchronized FlujoIniciado iniciarFlujoAsync(String proveedor) {
        String p = proveedor.toUpperCase();
        if (!p.equals("GOOGLE") && !p.equals("MICROSOFT")) {
            throw new IllegalArgumentException("Proveedor OAuth no soportado: " + proveedor);
        }
        if (flujoActivoId != null) {
            EstadoFlujo activo = flujos.get(flujoActivoId);
            if (activo != null && FLUJO_PENDIENTE.equals(activo.estado())) {
                throw new IllegalStateException(
                        "Ya hay un flujo OAuth en curso — espera a que termine o caduque");
            }
        }

        depurarFlujosTerminados();

        String flujoId = generarFlujoId();
        String authUrl = iniciarFlujo(p);  // genera y registra el state
        flujos.put(flujoId, new EstadoFlujo(FLUJO_PENDIENTE, null, null));
        flujoActivoId = flujoId;

        Thread hilo = new Thread(() -> ejecutarFlujo(flujoId, p), "oauth-flujo-" + flujoId);
        hilo.setDaemon(true);
        hilo.start();

        log.info("Flujo OAuth asíncrono iniciado id={} proveedor={}", flujoId, p);
        return new FlujoIniciado(flujoId, authUrl);
    }

    /**
     * Estado de un flujo. Los tokens solo viajan en COMPLETADO.
     */
    public EstadoFlujo estadoFlujo(String flujoId) {
        EstadoFlujo estado = flujos.get(flujoId);
        if (estado == null) {
            throw new IllegalArgumentException("Flujo OAuth desconocido o expirado: " + flujoId);
        }
        return estado;
    }

    /** Ejecuta espera + canje en background y registra el resultado. */
    private void ejecutarFlujo(String flujoId, String proveedor) {
        try {
            OAuthSession session = esperarCallback(proveedor, 130);
            flujos.put(flujoId, new EstadoFlujo(FLUJO_COMPLETADO, session, null));
            log.info("Flujo OAuth {} completado para {}", flujoId, session.email());
        } catch (TimeoutException e) {
            flujos.put(flujoId, new EstadoFlujo(FLUJO_TIMEOUT, null,
                    "Tiempo de espera agotado para el callback OAuth"));
            log.warn("Flujo OAuth {} timeout", flujoId);
        } catch (Exception e) {
            flujos.put(flujoId, new EstadoFlujo(FLUJO_ERROR, null, e.getMessage()));
            log.warn("Flujo OAuth {} error: {}", flujoId, e.getMessage());
        }
    }

    /** Id aleatorio de flujo (mismo formato que el state). */
    private String generarFlujoId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Si hay demasiados flujos retenidos, borra los terminados más viejos. */
    private void depurarFlujosTerminados() {
        if (flujos.size() <= MAX_FLUJOS_RETENIDOS) return;
        flujos.entrySet().removeIf(e -> !FLUJO_PENDIENTE.equals(e.getValue().estado())
                && !e.getKey().equals(flujoActivoId));
    }

    // ── Callback y canje ────────────────────────────────────────

    /**
     * Inicia el servidor callback, espera el resultado, canjea el código por tokens.
     *
     * @param proveedor GOOGLE | MICROSOFT
     * @param timeoutSeconds tiempo máximo de espera
     * @return tokens OAuth obtenidos
     */
    public OAuthSession esperarCallback(String proveedor, int timeoutSeconds)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {

        String tokenUrl = "GOOGLE".equalsIgnoreCase(proveedor)
                ? GoogleOAuthProvider.TOKEN_URL
                : MicrosoftOAuthProvider.TOKEN_URL;
        String clientId = "GOOGLE".equalsIgnoreCase(proveedor) ? googleClientId : microsoftClientId;
        String clientSecret = "GOOGLE".equalsIgnoreCase(proveedor) ? googleClientSecret : microsoftClientSecret;
        String redirectUri = "http://localhost:" + CALLBACK_PORT + "/oauth/callback";

        var server = new OAuthCallbackServer(CALLBACK_PORT);
        server.start();

        try {
            var result = server.waitForCallback(timeoutSeconds);
            String state = result.state();

            // Validar state (anti-CSRF + anti-replay)
            Boolean wasActive = ACTIVE_OAUTH_STATES.remove(state);
            if (wasActive == null || !wasActive) {
                throw new OAuth2Exception("State inválido o ya usado — posible ataque CSRF");
            }

            // Canjear código por tokens
            return canjearCodigo(tokenUrl, clientId, clientSecret, redirectUri, result.code(), proveedor);
        } catch (CancellationException | TimeoutException e) {
            server.stop();
            throw new TimeoutException("Tiempo de espera agotado para el callback OAuth");
        }
    }

    /**
     * Canjea el código de autorización por tokens y devuelve la sesión completa.
     */
    public OAuthSession canjearCodigo(String tokenUrl, String clientId, String clientSecret,
                                       String redirectUri, String code, String proveedor) {
        try {
            RestClient rc = restClientConTimeout();
            String body = "grant_type=authorization_code"
                    + "&code=" + code
                    + "&redirect_uri=" + redirectUri
                    + "&client_id=" + clientId
                    + "&client_secret=" + clientSecret;

            String response = rc.post()
                    .uri(tokenUrl)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode json = mapper.readTree(response);
            String accessToken = json.path("access_token").asText();
            String refreshToken = json.path("refresh_token").asText("");
            long expiresIn = json.path("expires_in").asLong(3600);
            long expiresAt = System.currentTimeMillis() + (expiresIn * 1000);

            // Obtener email del usuario desde el perfil
            String email = obtenerEmail(proveedor, accessToken);

            return new OAuthSession(proveedor, email, accessToken, refreshToken, expiresAt);
        } catch (Exception e) {
            throw new OAuth2Exception("Error al canjear código OAuth: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene el email del usuario autenticado desde la API de perfil del proveedor.
     */
    private String obtenerEmail(String proveedor, String accessToken) {
        try {
            String profileUrl = "GOOGLE".equalsIgnoreCase(proveedor)
                    ? GoogleOAuthProvider.PROFILE_URL
                    : MicrosoftOAuthProvider.PROFILE_URL;

            RestClient rc = restClientConTimeout();
            String response = rc.get()
                    .uri(profileUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            JsonNode json = mapper.readTree(response);

            if ("GOOGLE".equalsIgnoreCase(proveedor)) {
                return json.path("email").asText();
            } else {
                // Microsoft: el email está en mail o userPrincipalName
                String email = json.path("mail").asText("");
                if (email.isBlank()) {
                    email = json.path("userPrincipalName").asText("");
                }
                return email;
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener el email del perfil OAuth: {}", e.getMessage());
            return "oauth-" + System.currentTimeMillis() + "@localhost";
        }
    }

    /**
     * Renueva un access token usando el refresh token.
     */
    public OAuthTokenResult renovarToken(String proveedor, String refreshToken) {
        String tokenUrl = "GOOGLE".equalsIgnoreCase(proveedor)
                ? GoogleOAuthProvider.TOKEN_URL
                : MicrosoftOAuthProvider.TOKEN_URL;
        String clientId = "GOOGLE".equalsIgnoreCase(proveedor) ? googleClientId : microsoftClientId;
        String clientSecret = "GOOGLE".equalsIgnoreCase(proveedor) ? googleClientSecret : microsoftClientSecret;

        try {
            RestClient rc = restClientConTimeout();
            String body = "grant_type=refresh_token"
                    + "&refresh_token=" + refreshToken
                    + "&client_id=" + clientId
                    + "&client_secret=" + clientSecret;

            String response = rc.post()
                    .uri(tokenUrl)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode json = mapper.readTree(response);
            String accessToken = json.path("access_token").asText();
            String newRefreshToken = json.path("refresh_token").asText(refreshToken);
            long expiresIn = json.path("expires_in").asLong(3600);
            long expiresAt = System.currentTimeMillis() + (expiresIn * 1000);

            return new OAuthTokenResult(accessToken, newRefreshToken, expiresAt);
        } catch (Exception e) {
            throw new OAuth2Exception("Error al renovar token OAuth: " + e.getMessage(), e);
        }
    }

    /**
     * RestClient con connect/read timeout — evita hilos colgados si Google o
     * Microsoft no responden. AiService y TodoistService ya usan builder+timeout.
     */
    private RestClient restClientConTimeout() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory).build();
    }

    /** Resultado del refresco de tokens (sin email). */
    public record OAuthTokenResult(String accessToken, String refreshToken, long expiresAt) {}
}
