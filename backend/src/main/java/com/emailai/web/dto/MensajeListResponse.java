package com.emailai.web.dto;

import java.util.List;

public record MensajeListResponse(
    List<MensajeResponse> mensajes,
    long total,
    int pagina,
    int porPagina
) {}
