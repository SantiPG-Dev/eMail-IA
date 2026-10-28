package com.emailai.web.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.emailai.domain.entities.EventoCalendario;
import com.emailai.service.EventoCalendarioService;
import com.emailai.service.IcsService;
import com.emailai.web.dto.EventoRequest;
import com.emailai.web.dto.EventoResponse;

// CRUD de eventos de calendario (locales + importados de ICS)
@RestController
@RequestMapping("/api/calendario")
public class CalendarController {

    private static final long MAX_ICS_BYTES = 5L * 1024 * 1024;

    private final EventoCalendarioService eventoService;
    private final IcsService icsService;

    public CalendarController(EventoCalendarioService eventoService, IcsService icsService) {
        this.eventoService = eventoService;
        this.icsService = icsService;
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

    /** Importa un .ics subido desde la UI (multipart, máx. 5 MB). */
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importar(@RequestParam("archivo") MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Archivo ICS vacío"));
        }
        if (archivo.getSize() > MAX_ICS_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("error", "El archivo supera 5 MB"));
        }
        try {
            String contenido = new String(archivo.getBytes(), StandardCharsets.UTF_8);
            var eventos = icsService.procesarContenido(contenido);
            return ResponseEntity.ok(Map.of("ok", true, "importados", eventos.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No se pudo leer el ICS: " + e.getMessage()));
        }
    }

    /** Descarga el calendario local como .ics (VCALENDAR vacío si no hay eventos). */
    @GetMapping(value = "/exportar", produces = "text/calendar")
    public ResponseEntity<String> exportar() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"emailai-calendario.ics\"")
                .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .body(icsService.generarICS());
    }

    private EventoResponse toResponse(EventoCalendario e) {
        return new EventoResponse(e.getId(), e.getFecha(), e.getHora(),
                e.isTodoElDia(), e.getFechaFin(), e.getHoraFin(),
                e.getTitulo(), e.getDetalle(), e.getOrigen(), e.getMensajeId());
    }
}
