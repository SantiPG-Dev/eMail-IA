package com.emailai.web.dto;

public record ContactoResponse(
    Integer id,
    String nombre,
    String apellido,
    String email,
    String telefono,
    String notas
) {}
