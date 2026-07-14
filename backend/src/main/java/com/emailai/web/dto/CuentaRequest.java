package com.emailai.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CuentaRequest(
    @NotBlank String nombre,
    @NotBlank String email,
    String servidor,
    Integer puerto,
    String usuario,
    String password,
    boolean esDefault,
    String oauthProvider,
    String oauthAccessToken,
    String oauthRefreshToken,
    Long oauthExpiresAt
) {}
