package com.emailai.web.dto;

public record CuentaResponse(
    Integer id,
    String nombre,
    String email,
    String servidor,
    Integer puerto,
    String tipoConexion,
    boolean esDefault,
    String oauthProvider
) {}
