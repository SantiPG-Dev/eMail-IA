package com.emailai.web.dto;

public record EventoRequest(
    String fecha, String hora,
    Boolean todoElDia, String fechaFin, String horaFin,
    String titulo, String detalle, String origen, Integer mensajeId
) {}
