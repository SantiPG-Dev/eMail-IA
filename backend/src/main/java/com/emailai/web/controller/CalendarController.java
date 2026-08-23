package com.emailai.web.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.emailai.domain.entities.EventoCalendario;
import com.emailai.service.EventoCalendarioService;
import com.emailai.web.dto.EventoRequest;
import com.emailai.web.dto.EventoResponse;

// CRUD de eventos de calendario (locales + importados de ICS)
@RestController
@RequestMapping("/api/calendario")
public class CalendarController {

    private final EventoCalendarioService eventoService;

    public CalendarController(EventoCalendarioService eventoService) {
        this.eventoService = eventoService;
    }

    @GetMapping
    public List<EventoResponse> listar() {
        return eventoService.listarTodos().stream().map(this::toResponse).toList();
    }

    @GetMapping("/fecha/{fecha}")
    public List<EventoResponse> listarPorFecha(@PathVariable String fecha) {
        return eventoService.listarPorFecha(LocalDate.parse(fecha)).stream()
                .map(this::toResponse).toList();
    }

    @GetMapping("/fechas-con-eventos")
    public List<String> fechasConEventos() {
        return eventoService.fechasConEventos();
    }

    @GetMapping("/{id}")
    public EventoResponse obtener(@PathVariable Integer id) {
        return toResponse(eventoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<EventoResponse> crear(@RequestBody EventoRequest req) {
        EventoCalendario e = new EventoCalendario();
        aplicarRequest(e, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(eventoService.guardar(e)));
    }

    @PutMapping("/{id}")
    public EventoResponse actualizar(@PathVariable Integer id, @RequestBody EventoRequest req) {
        EventoCalendario e = new EventoCalendario();
        aplicarRequest(e, req);
        return toResponse(eventoService.actualizar(id, e));
    }

    private void aplicarRequest(EventoCalendario e, EventoRequest req) {
        e.setFecha(req.fecha());
        e.setHora(req.hora());
        e.setTodoElDia(Boolean.TRUE.equals(req.todoElDia()));
        e.setFechaFin(req.fechaFin());
        e.setHoraFin(req.horaFin());
        e.setTitulo(req.titulo());
        e.setDetalle(req.detalle());
        e.setOrigen(req.origen() != null ? req.origen() : "local");
        e.setMensajeId(req.mensajeId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        eventoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private EventoResponse toResponse(EventoCalendario e) {
        return new EventoResponse(e.getId(), e.getFecha(), e.getHora(),
                e.isTodoElDia(), e.getFechaFin(), e.getHoraFin(),
                e.getTitulo(), e.getDetalle(), e.getOrigen(), e.getMensajeId());
    }
}
