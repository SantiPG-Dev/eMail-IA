package com.emailai.oauth;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

// Orquesta el flujo OAuth2 completo: URL de auth → callback local → tokens.
@Service
public class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CALLBACK_PORT = 9876;

    /** Mapa de states activos (CSRF + anti-replay). */
    public static final ConcurrentHashMap<String, Boolean> ACTIVE_OAUTH_STATES = new ConcurrentHashMap<>();

    private final ObjectMapper mapper;

    public OAuthService() {
        this.mapper = new ObjectMapper();
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

    /**
     * Inicia el flujo OAuth: genera URL, inicia servidor callback, abre navegador.
     *
     * @return URL de autorización para abrir en el navegador
     */
    public String iniciarFlujoGoogle(String clientId, String clientSecret) {
        String state = generarState();
        GoogleOAuthProvider provider = new GoogleOAuthProvider(clientId, clientSecret,
                "http://localhost:" + CALLBACK_PORT + "/oauth/callback");
        return provider.generarUrlAutorizacion(state);
    }

    /**
     * Inicia el flujo OAuth Microsoft.
     */
    public String iniciarFlujoMicrosoft(String clientId, String clientSecret) {
        String state = generarState();
        MicrosoftOAuthProvider provider = new MicrosoftOAuthProvider(clientId, clientSecret,
                "http://localhost:" + CALLBACK_PORT + "/oauth/callback");
        return provider.generarUrlAutorizacion(state);
    }

    /**
     * Espera el callback OAuth y canjea el código por tokens.
     */
    public OAuthTokenResult esperarCallbackYCanjear(String tokenUrl, String clientId,
                                                      String clientSecret, String redirectUri,
                                                      int timeoutSeconds)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        OAuthCallbackServer server = new OAuthCallbackServer(CALLBACK_PORT);
        server.start();

        try {
            OAuthCallbackServer.OAuthCallbackResult result = server.waitForCallback(timeoutSeconds);
            String state = result.state();

            // Validar state (anti-CSRF + anti-replay)
            Boolean wasActive = ACTIVE_OAUTH_STATES.remove(state);
            if (wasActive == null || !wasActive) {
                throw new OAuth2Exception("State inválido o ya usado — posible ataque CSRF");
            }

            // Canjear código por tokens
            return canjearCodigo(tokenUrl, clientId, clientSecret, redirectUri, result.code());
        } catch (CancellationException | TimeoutException e) {
            server.stop();
            throw new TimeoutException("Tiempo de espera agotado para el callback OAuth");
        }
    }

    /**
     * Canjea el código de autorización por tokens (access + refresh).
     */
    public OAuthTokenResult canjearCodigo(String tokenUrl, String clientId, String clientSecret,
                                           String redirectUri, String code) {
        try {
            RestClient rc = RestClient.create();
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

            return new OAuthTokenResult(accessToken, refreshToken, expiresAt);
        } catch (Exception e) {
            throw new OAuth2Exception("Error al canjear código OAuth: " + e.getMessage(), e);
        }
    }

    /**
     * Renueva un access token usando el refresh token.
     */
    public OAuthTokenResult renovarToken(String tokenUrl, String clientId, String clientSecret,
                                          String refreshToken) {
        try {
            RestClient rc = RestClient.create();
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

    /** Resultado del flujo de tokens. */
    public record OAuthTokenResult(String accessToken, String refreshToken, long expiresAt) {}
}
