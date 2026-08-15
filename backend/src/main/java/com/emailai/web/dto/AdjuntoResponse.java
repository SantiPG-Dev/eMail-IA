package com.emailai.web.dto;

// Metadatos de un adjunto para el listado/detalle del mensaje.
// Los datos binarios se sirven por GET /api/mensajes/{id}/adjuntos/{adjId}.
public record AdjuntoResponse(
    Long id,
    String nombre,
    String mimeType,
    Long tamanoBytes
) {}
