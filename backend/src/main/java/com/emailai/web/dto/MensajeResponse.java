package com.emailai.web.dto;

import java.util.List;

public record MensajeResponse(
    Long id, String uid, String cuentaHash, String carpetaImap,
    String remitente, String destinatarios, String cc, String cco,
    String asunto, String cuerpo, String html,
    String categoria, String prioridad, String fechaRecepcion,
    List<AdjuntoResponse> adjuntos
) {}
