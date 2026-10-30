package com.emailai.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import com.emailai.domain.entities.ItemAgenda;
import com.emailai.service.AgendaService;
import com.emailai.web.dto.ItemAgendaRequest;
import com.emailai.web.dto.ItemAgendaResponse;

// Vista de Tareas sobre la agenda unica: incluye eventos (creados en el
// calendario) y tareas sin fecha. Los creados aqui son TAREA.
@RestController
@RequestMapping("/api/tareas")
public class TareaController {

    private final AgendaService agendaService;

    public TareaController(AgendaService agendaService) {
        this.agendaService = agendaService;
    }

    @GetMapping
    public List<ItemAgendaResponse> listar() {
        return agendaService.listarTareas().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ItemAgendaResponse obtener(@PathVariable Integer id) {
        return toResponse(agendaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<ItemAgendaResponse> crear(@Valid @RequestBody ItemAgendaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(agendaService.crearTarea(aplicar(new ItemAgenda(), req))));
    }

    @PutMapping("/{id}")
    public ItemAgendaResponse actualizar(@PathVariable Integer id, @Valid @RequestBody ItemAgendaRequest req) {
        return toResponse(agendaService.actualizarTarea(id, aplicar(new ItemAgenda(), req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        agendaService.eliminar(id);
        return ResponseEntity.noContent().build();
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
