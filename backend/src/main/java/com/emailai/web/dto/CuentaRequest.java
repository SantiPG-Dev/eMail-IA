package com.emailai.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CuentaRequest(
    @NotBlank String nombre,
    @NotBlank @Email String email,
    // Hostname/IP válido: bloquea path traversal y valores arbitrarios que
    // luego se usan como servidor IMAP/SMTP (evita exfiltrar credenciales
    // a un servidor controlado por un tercero). Null permitido (cuentas OAuth).
    @Pattern(regexp = "^[A-Za-z0-9]([A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?)*$",
             message = "servidor debe ser un hostname o IP válido") String servidor,
    Integer puerto,
    String usuario,
    String password,
    String tipoConexion,           // IMAP | POP3
    boolean esDefault,
    String oauthProvider,
    String oauthAccessToken,
    String oauthRefreshToken,
    Long oauthExpiresAt
) {}
