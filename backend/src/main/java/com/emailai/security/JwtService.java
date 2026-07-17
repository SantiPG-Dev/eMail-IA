package com.emailai.security;

// JWT con HMAC-SHA256. Clave de 256 bits generada aleatoriamente (no hardcodeada).
// Tokens de 24h, incluyen subject (email) y fecha de expiración.
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servicio JWT para generar y validar tokens de sesión local.
 *
 * <p>Usa una clave HMAC-SHA256 derivada de una clave secreta configurable.
 * El token expira tras el tiempo configurado (por defecto 24h).
 * NO es un JWT multi-usuario como EazyPlanIA, sino una sesión local
 * para la app de escritorio.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${emailai.security.jwt.secret:}") String secret,
            @Value("${emailai.security.jwt.expiration-hours:24}") int expirationHours) {
        String key = secret;
        if (key == null || key.isBlank()) {
            // Generar clave aleatoria de 32 bytes si no hay configurada
            byte[] randomKey = new byte[32];
            new SecureRandom().nextBytes(randomKey);
            key = Base64.getEncoder().encodeToString(randomKey);
            log.info("JWT: clave aleatoria generada (no persistente)");
        }
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        // Asegurar 256 bits para HS256
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationHours * 3600L * 1000L;
    }

    /**
     * Genera un token JWT para la sesión.
     */
    public String generateToken(String subject) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Extrae el subject (usuario) de un token JWT.
     */
    public String extractSubject(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Valida un token JWT.
     */
    public boolean isValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
