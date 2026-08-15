package com.emailai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.emailai.domain.entities.Cuenta;
import com.emailai.oauth.OAuthService;
import com.emailai.oauth.OAuthService.OAuthTokenResult;
import com.emailai.security.CredentialService;

// Resuelve las credenciales IMAP/SMTP de una cuenta: password o OAuth2.
// Para OAuth2 refresca el access token si está caducado (o a punto) y
// persiste el renovado en la cuenta — antes el token expirado rompía
// toda sincronización sin intentar renovarlo.
@Service
public class CredencialesMailService {

    private static final Logger log = LoggerFactory.getLogger(CredencialesMailService.class);

    /** Margen de anticipación del refresh: 2 minutos antes de caducar. */
    private static final long MARGEN_REFRESH_MS = 2 * 60 * 1000;

    private final CuentaService cuentaService;
    private final CredentialService credentialService;
    private final OAuthService oauthService;

    public CredencialesMailService(CuentaService cuentaService,
                                    CredentialService credentialService,
                                    OAuthService oauthService) {
        this.cuentaService = cuentaService;
        this.credentialService = credentialService;
        this.oauthService = oauthService;
    }

    /** Credenciales resueltas listas para MailService. */
    public record Credenciales(String user, String secret, boolean esOAuth) {}

    /**
     * Resuelve las credenciales de la cuenta: password si las tiene,
     * OAuth2 (con refresh si procede) si no. Nunca devuelve el email
     * como fallback de password — eso autentica contra el servidor real.
     *
     * @return credenciales, o null si la cuenta no tiene ninguna válida.
     */
    public Credenciales resolver(Cuenta cuenta) {
        // 1. Password clásico (cuentas sin OAuth)
        if (cuenta.getOauthProvider() == null) {
            String pass = credentialService.descifrar(cuenta.getPasswordCifrada());
            if (pass != null && !pass.isBlank()) {
                return new Credenciales(cuenta.getEmail(), pass, false);
            }
            return null;
        }

        // 2. Cuenta OAuth2
        String accessToken = credentialService.descifrar(cuenta.getOauthAccessToken());
        String refreshToken = credentialService.descifrar(cuenta.getOauthRefreshToken());
        long expiraEn = (cuenta.getOauthExpiresAt() != null ? cuenta.getOauthExpiresAt() : 0)
                - System.currentTimeMillis();

        if (accessToken != null && !accessToken.isBlank() && expiraEn > MARGEN_REFRESH_MS) {
            return new Credenciales(cuenta.getEmail(), accessToken, true);
        }

        // Token caducado o ausente: intentar refresh
        log.info("Access token OAuth caducado o ausente para {} — renovando", cuenta.getEmail());
        String renovado = refrescarYPersistir(cuenta, refreshToken);
        if (renovado != null) {
            return new Credenciales(cuenta.getEmail(), renovado, true);
        }

        // Renovación fallida pero queda access token: probar con lo que hay
        if (accessToken != null && !accessToken.isBlank()) {
            log.warn("No se pudo renovar el token de {} — intentando con el actual", cuenta.getEmail());
            return new Credenciales(cuenta.getEmail(), accessToken, true);
        }
        return null;
    }

    /**
     * Refresca el access token y persiste tokens + expiración en la cuenta.
     * @return nuevo access token, o null si falló la renovación.
     */
    private String refrescarYPersistir(Cuenta cuenta, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("Sin refresh token para {} — re-autenticación OAuth necesaria", cuenta.getEmail());
            return null;
        }
        try {
            OAuthTokenResult res = oauthService.renovarToken(cuenta.getOauthProvider(), refreshToken);
            cuenta.setOauthAccessToken(credentialService.cifrar(res.accessToken()));
            if (res.refreshToken() != null && !res.refreshToken().isBlank()) {
                cuenta.setOauthRefreshToken(credentialService.cifrar(res.refreshToken()));
            }
            cuenta.setOauthExpiresAt(res.expiresAt());
            cuentaService.guardar(cuenta);
            log.info("Token OAuth renovado y persistido para {}", cuenta.getEmail());
            return res.accessToken();
        } catch (Exception e) {
            log.warn("Renovación OAuth fallida para {}: {}", cuenta.getEmail(), e.getMessage());
            return null;
        }
    }
}
