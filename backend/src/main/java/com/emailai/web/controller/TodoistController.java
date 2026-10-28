package com.emailai.web.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.emailai.domain.entities.Tarea;
import com.emailai.service.TareaService;
import com.emailai.service.TodoistService;

// Integración con Todoist: estado, prueba de conexión, envío de tareas.
// La API key se configura desde Ajustes → Integraciones (se guarda cifrada).
@RestController
@RequestMapping("/api/todoist")
public class TodoistController {

    private final TodoistService todoistService;
    private final TareaService tareaService;

    public TodoistController(TodoistService todoistService, TareaService tareaService) {
        this.todoistService = todoistService;
        this.tareaService = tareaService;
    }

    @GetMapping("/estado")
    public Map<String, Object> estado() {
        return Map.of("configurado", todoistService.isEnabled());
    }

    /** Guarda (o borra, con apiKey vacía) la key. No la devuelve nunca. */
    @PostMapping("/config")
    public Map<String, Object> configurar(@RequestParam(required = false) String apiKey) {
        todoistService.guardarApiKey(apiKey);
        return Map.of("configurado", todoistService.isEnabled());
    }

    /** Prueba real contra Todoist: cuenta las tareas de la primera página. */
    @PostMapping("/probar")
    public ResponseEntity<?> probar() {
        if (!todoistService.isEnabled()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false, "error", "No hay API key de Todoist configurada"));
        }
        try {
            List<?> tareas = todoistService.listarTareas();
            return ResponseEntity.ok(Map.of("ok", true, "tareas", tareas.size()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "ok", false, "error", e.getMessage() != null ? e.getMessage() : "sin detalle"));
        }
    }

    /** Envía una tarea local a Todoist. Idempotente: no reenvía la ya enviada. */
    @PostMapping("/tareas/{id}")
    public ResponseEntity<?> enviarTarea(@PathVariable Integer id) {
        Tarea t = tareaService.buscarPorId(id);
        if (t.getTodoistId() != null && !t.getTodoistId().isBlank()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "La tarea ya está en Todoist"));
        }
        if (!todoistService.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No hay API key de Todoist configurada"));
        }
        String remoto = todoistService.crearTarea(t.getTitulo(), t.getFechaVencimiento(), t.getPrioridad());
        if (remoto == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Todoist no aceptó la tarea (revisa la key o la conexión)"));
        }
        t.setTodoistId(remoto);
        tareaService.guardar(t);
        return ResponseEntity.ok(Map.of("ok", true, "todoistId", remoto));
    }
}
