package com.emailai.web.dto;

import jakarta.validation.constraints.NotBlank;

// Mismo cuerpo para crear/editar desde Calendario o Tareas. El tipo lo pone
// el endpoint (POST) o se conserva el existente (PUT).
public record ItemAgendaRequest(
    @NotBlank String titulo,
    String detalle,
    String fecha, String hora, Boolean todoElDia, String fechaFin, String horaFin,
    String estado, String etiquetas, String prioridad,
    String origen, Integer mensajeId
) {}
