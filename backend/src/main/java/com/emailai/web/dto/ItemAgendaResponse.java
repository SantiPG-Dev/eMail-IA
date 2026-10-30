package com.emailai.web.dto;

public record ItemAgendaResponse(
    Integer id, String tipo, String titulo, String detalle,
    String fecha, String hora, boolean todoElDia, String fechaFin, String horaFin,
    String estado, String prioridad, String etiquetas,
    String origen, Integer mensajeId, String todoistId
) {}
