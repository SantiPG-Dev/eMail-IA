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
