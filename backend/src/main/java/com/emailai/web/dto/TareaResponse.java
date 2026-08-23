package com.emailai.web.dto;

public record TareaResponse(
    Integer id, String titulo, String descripcion,
    String fechaVencimiento, String estado, String etiquetas, String prioridad,
    Integer mensajeId
) {}
