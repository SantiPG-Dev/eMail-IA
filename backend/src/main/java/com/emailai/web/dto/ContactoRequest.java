package com.emailai.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ContactoRequest(
    @NotBlank String nombre,
    String apellido,
    String email,
    String telefono,
    String notas
) {}
