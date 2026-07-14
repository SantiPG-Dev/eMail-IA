package com.emailai.web.dto;

public record MensajeResponse(
    Long id, String uid, String cuentaHash, String carpetaImap,
    String remitente, String destinatarios, String cc, String cco,
    String asunto, String cuerpo, String html,
    String categoria, String prioridad, String fechaRecepcion
) {}
