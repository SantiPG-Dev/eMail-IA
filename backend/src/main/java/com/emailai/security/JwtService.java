package com.emailai.security;

// JWT con HMAC-SHA256. Clave de 256 bits generada aleatoriamente (no hardcodeada).
// Tokens de 24h, incluyen subject (email) y fecha de expiración.
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * <p>Prioridad de clave de firmado:
 * <ol>
 *   <li>Variable de entorno EMAILAI_JWT_SECRET (para despliegues controlados)</li>
 *   <li>Archivo &lt;data-dir&gt;/jwt.key persistido entre reinicios (generado auto)</li>
 * </ol>
 * Nunca se usa una clave hardcodeada por defecto.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${emailai.security.jwt.secret:}") String envSecret,
            @Value("${emailai.security.jwt.expiration-hours:24}") int expirationHours,
            @Value("${emailai.data-dir:DB}") String dataDir) {
        String key = resolveSigningKey(envSecret, dataDir);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationHours * 3600L * 1000L;
    }

    /**
     * Resuelve la clave de firmado con prioridad: env var → archivo persistido → generado.
     * La clave generada se guarda en &lt;data-dir&gt;/jwt.key para persistir entre reinicios.
     */
    private String resolveSigningKey(String envSecret, String dataDir) {
        // 1) Env var explícita (despliegue controlado)
        if (envSecret != null && !envSecret.isBlank()) {
            log.info("JWT: clave cargada desde EMAILAI_JWT_SECRET");
            return ensureLength(envSecret);
        }

        // 2) Archivo persistido en data-dir
        Path keyFile = Paths.get(dataDir, "jwt.key");
        try {
            if (Files.exists(keyFile)) {
                String persisted = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
                if (!persisted.isBlank()) {
                    log.info("JWT: clave cargada desde {}", keyFile);
                    return persisted;
                }
            }

            // 3) Generar clave nueva de 64 bytes (512 bits > 256 mínimo HS256)
            byte[] randomKey = new byte[64];
            new SecureRandom().nextBytes(randomKey);
            String generated = Base64.getEncoder().encodeToString(randomKey);

            // Guardar con permisos restrictivos (owner-only)
            Files.createDirectories(keyFile.getParent());
            Files.writeString(keyFile, generated, StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(keyFile, java.util.Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // Windows no soporta POSIX perms
            }
            log.info("JWT: clave nueva generada y persistida en {}", keyFile);
            return generated;
        } catch (Exception e) {
            // Fallback en memoria si no se puede escribir el archivo
            log.warn("JWT: no se pudo persistir clave ({}), usando clave en memoria no persistente", e.getMessage());
            byte[] randomKey = new byte[64];
            new SecureRandom().nextBytes(randomKey);
            return Base64.getEncoder().encodeToString(randomKey);
        }
    }

    /**
     * Rellena a 64 bytes si la clave de env var es más corta (HS256 necesita >= 32).
     */
    private String ensureLength(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length >= 32) return key;
        byte[] padded = new byte[64];
        System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 64));
        return new String(padded, StandardCharsets.UTF_8);
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
