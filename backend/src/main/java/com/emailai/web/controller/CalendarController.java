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

import jakarta.validation.Valid;

import com.emailai.domain.entities.ItemAgenda;
import com.emailai.service.AgendaService;
import com.emailai.service.IcsService;
import com.emailai.web.dto.ItemAgendaRequest;
import com.emailai.web.dto.ItemAgendaResponse;

// Vista de Calendario sobre la agenda unica: solo elementos con fecha.
// Los creados aqui son EVENTO; los creados en /api/tareas son TAREA.
@RestController
@RequestMapping("/api/calendario")
public class CalendarController {

    private static final long MAX_ICS_BYTES = 5L * 1024 * 1024;

    private final AgendaService agendaService;
    private final IcsService icsService;

    public CalendarController(AgendaService agendaService, IcsService icsService) {
        this.agendaService = agendaService;
        this.icsService = icsService;
    }

    @GetMapping
    public List<ItemAgendaResponse> listar() {
        return agendaService.listarCalendario().stream().map(this::toResponse).toList();
    }

    @GetMapping("/fecha/{fecha}")
    public List<ItemAgendaResponse> listarPorFecha(@PathVariable String fecha) {
        return agendaService.listarPorFecha(LocalDate.parse(fecha)).stream()
                .map(this::toResponse).toList();
    }

    @GetMapping("/fechas-con-eventos")
    public List<String> fechasConEventos() {
        return agendaService.fechasConItems();
    }

    @GetMapping("/{id}")
    public ItemAgendaResponse obtener(@PathVariable Integer id) {
        return toResponse(agendaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<ItemAgendaResponse> crear(@Valid @RequestBody ItemAgendaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(agendaService.crearEvento(aplicar(new ItemAgenda(), req))));
    }

    @PutMapping("/{id}")
    public ItemAgendaResponse actualizar(@PathVariable Integer id, @Valid @RequestBody ItemAgendaRequest req) {
        return toResponse(agendaService.actualizarEvento(id, aplicar(new ItemAgenda(), req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        agendaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** Importa un .ics subido desde la UI (multipart, max. 5 MB). */
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importar(@RequestParam("archivo") MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Archivo ICS vacio"));
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
                    "error", "No se pudo leer el ICS: " + (e.getMessage() != null ? e.getMessage() : "sin detalle")));
        }
    }

    /** Descarga la agenda con fecha como .ics (VCALENDAR vacio si no hay nada). */
    @GetMapping(value = "/exportar", produces = "text/calendar")
    public ResponseEntity<String> exportar() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"emailai-calendario.ics\"")
                .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .body(icsService.generarICS());
    }

    private ItemAgenda aplicar(ItemAgenda i, ItemAgendaRequest req) {
        i.setTitulo(req.titulo());
        i.setDetalle(req.detalle());
        i.setFecha(req.fecha());
        i.setHora(req.hora());
        i.setTodoElDia(Boolean.TRUE.equals(req.todoElDia()));
        i.setFechaFin(req.fechaFin());
        i.setHoraFin(req.horaFin());
        i.setEstado(req.estado());
        i.setEtiquetas(req.etiquetas());
        i.setPrioridad(req.prioridad());
        i.setOrigen(req.origen() != null ? req.origen() : "local");
        i.setMensajeId(req.mensajeId());
        return i;
    }

    private ItemAgendaResponse toResponse(ItemAgenda i) {
        return new ItemAgendaResponse(i.getId(), i.getTipo(), i.getTitulo(), i.getDetalle(),
                i.getFecha(), i.getHora(), i.isTodoElDia(), i.getFechaFin(), i.getHoraFin(),
                i.getEstado(), i.getPrioridad(), i.getEtiquetas(),
                i.getOrigen(), i.getMensajeId(), i.getTodoistId());
    }
}
