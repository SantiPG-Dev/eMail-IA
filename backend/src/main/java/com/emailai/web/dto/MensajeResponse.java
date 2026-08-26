package com.emailai.web.dto;

import java.util.List;

public record MensajeResponse(
    Long id, String uid, String cuentaHash, String carpetaImap,
    String remitente, String destinatarios, String cc, String cco,
    String asunto, String cuerpo, String html,
    String categoria, String prioridad, String fechaRecepcion,
    List<AdjuntoResponse> adjuntos,
    Integer reclasificados
) {
    public MensajeResponse(Long id, String uid, String cuentaHash, String carpetaImap,
            String remitente, String destinatarios, String cc, String cco,
            String asunto, String cuerpo, String html,
            String categoria, String prioridad, String fechaRecepcion,
            List<AdjuntoResponse> adjuntos) {
        this(id, uid, cuentaHash, carpetaImap, remitente, destinatarios, cc, cco,
                asunto, cuerpo, html, categoria, prioridad, fechaRecepcion, adjuntos, null);
    }
}
