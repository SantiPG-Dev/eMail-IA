package com.emailai.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import com.emailai.domain.entities.Tarea;
import com.emailai.service.TareaService;
import com.emailai.web.dto.TareaRequest;
import com.emailai.web.dto.TareaResponse;

// CRUD de tareas, con sincronización opcional a Todoist.
@RestController
@RequestMapping("/api/tareas")
public class TareaController {

    private final TareaService tareaService;

    public TareaController(TareaService tareaService) {
        this.tareaService = tareaService;
    }

    @GetMapping
    public List<TareaResponse> listar() {
        return tareaService.listarTodas().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public TareaResponse obtener(@PathVariable Integer id) {
        return toResponse(tareaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<TareaResponse> crear(@Valid @RequestBody TareaRequest req) {
        Tarea t = new Tarea();
        t.setTitulo(req.titulo());
        t.setDescripcion(req.descripcion());
        t.setFechaVencimiento(req.fechaVencimiento());
        t.setEstado(req.estado() != null ? req.estado() : "pendiente");
        t.setEtiquetas(req.etiquetas());
        t.setPrioridad(req.prioridad() != null ? req.prioridad() : "MEDIA");
        t.setMensajeId(req.mensajeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(tareaService.guardar(t)));
    }

    @PutMapping("/{id}")
    public TareaResponse actualizar(@PathVariable Integer id, @Valid @RequestBody TareaRequest req) {
        Tarea t = new Tarea();
        t.setTitulo(req.titulo());
        t.setDescripcion(req.descripcion());
        t.setFechaVencimiento(req.fechaVencimiento());
        t.setEstado(req.estado());
        t.setEtiquetas(req.etiquetas());
        t.setPrioridad(req.prioridad());
        t.setMensajeId(req.mensajeId());
        return toResponse(tareaService.actualizar(id, t));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        tareaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private TareaResponse toResponse(Tarea t) {
        return new TareaResponse(t.getId(), t.getTitulo(), t.getDescripcion(),
                t.getFechaVencimiento(), t.getEstado(), t.getEtiquetas(), t.getPrioridad(),
                t.getMensajeId());
    }
}
