package com.emailai.web.dto;

import jakarta.validation.constraints.NotBlank;

public record TareaRequest(
    @NotBlank String titulo,
    String descripcion, String fechaVencimiento,
    String estado, String etiquetas, String prioridad, Integer mensajeId
) {}
