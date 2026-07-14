package com.emailai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

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

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${emailai.security.jwt.secret:emailai-local-session-key-change-in-prod}") String secret,
            @Value("${emailai.security.jwt.expiration-hours:24}") int expirationHours) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
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
