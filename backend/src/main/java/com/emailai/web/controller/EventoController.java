package com.emailai.web.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.emailai.service.EventoSyncService;

// Stream SSE de eventos de la app (sync terminado, heartbeat). Cae bajo
// /api/** → el filtro JWT lo protege como el resto de la API.
@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    private final EventoSyncService eventoSyncService;

    public EventoController(EventoSyncService eventoSyncService) {
        this.eventoSyncService = eventoSyncService;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventos() {
        return eventoSyncService.registrar();
    }
}
