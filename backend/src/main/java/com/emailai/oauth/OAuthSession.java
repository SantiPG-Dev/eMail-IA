package com.emailai.oauth;

// Resultado completo de un flujo OAuth2: proveedor, email, tokens y expiración.
public record OAuthSession(
    String proveedor,
    String email,
    String accessToken,
    String refreshToken,
    long expiresAt
) {}
