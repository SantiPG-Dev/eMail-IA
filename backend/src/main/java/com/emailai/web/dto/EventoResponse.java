package com.emailai.web.dto;

public record EventoResponse(
    Integer id, String fecha, String hora,
    boolean todoElDia, String fechaFin, String horaFin,
    String titulo, String detalle, String origen, Integer mensajeId
) {}
