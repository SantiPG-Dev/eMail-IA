package com.emailai.oauth;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Canje y renovación de tokens OAuth contra los endpoints de Google/Microsoft,
// más el fetch del email de perfil. La orquestación del flujo (states, callback,
// flujos asíncronos) vive en OAuthService; esto es solo la parte HTTP de tokens.

@Service
public class OAuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenService.class);

    private final ObjectMapper mapper;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String microsoftClientId;
    private final String microsoftClientSecret;

    public OAuthTokenService(
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
     * Canjea el código de autorización por tokens y devuelve la sesión completa.
     */
    public OAuthSession canjearCodigo(String tokenUrl, String clientId, String clientSecret,
                                       String redirectUri, String code, String proveedor) {
        try {
            RestClient rc = restClientConTimeout();
            String body = formBody(
                    "grant_type", "authorization_code",
                    "code", code,
                    "redirect_uri", redirectUri,
                    "client_id", clientId,
                    "client_secret", clientSecret);

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
        } catch (IOException | RestClientException e) {
            throw new OAuth2Exception("Error al canjear código OAuth: " + e.getMessage(), e);
        }
    }

    /**
     * Body application/x-www-form-urlencoded con cada valor URL-encodeado.
     * Concatenar a mano rompería el body si un valor contiene &, = o +
     * (el clientSecret de Google/Microsoft puede traerlos).
     */
    static String formBody(String... paresNombreValor) {
        if (paresNombreValor.length % 2 != 0) {
            throw new IllegalArgumentException("formBody espera pares nombre-valor");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paresNombreValor.length; i += 2) {
            if (i > 0) sb.append('&');
            sb.append(URLEncoder.encode(paresNombreValor[i], StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(paresNombreValor[i + 1], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /**
     * Obtiene el email del usuario autenticado desde la API de perfil del proveedor.
     */
    private String obtenerEmail(String proveedor, String accessToken) {
        String email;
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
                email = json.path("email").asText("");
            } else {
                // Microsoft: el email está en mail o userPrincipalName
                email = json.path("mail").asText("");
                if (email.isBlank()) {
                    email = json.path("userPrincipalName").asText("");
                }
            }
        } catch (IOException | RestClientException e) {
            log.warn("No se pudo obtener el email del perfil OAuth: {}", e.getMessage());
            email = "";
        }
        // Antes se devolvía "oauth-<ts>@localhost": una cuenta fantasma que
        // rompía el login posterior (auditoría 2026-08-26). Fallar el flujo
        // con error claro es más seguro que persistir una cuenta inservible.
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new OAuth2Exception("El proveedor OAuth no devolvió un email válido "
                    + "(revisa los scopes del perfil) — flujo cancelado");
        }
        return email;
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
            String body = formBody(
                    "grant_type", "refresh_token",
                    "refresh_token", refreshToken,
                    "client_id", clientId,
                    "client_secret", clientSecret);

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
        } catch (IOException | RestClientException e) {
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
