package com.emailai.web.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.emailai.domain.entities.ItemAgenda;
import com.emailai.service.AgendaService;
import com.emailai.service.TodoistService;

// Integracion con Todoist: estado, prueba de conexion, envio de tareas.
// Solo se envian elementos de tipo TAREA; los eventos del calendario no.
// La API key se configura desde Ajustes -> Integraciones (se guarda cifrada).
@RestController
@RequestMapping("/api/todoist")
public class TodoistController {

    private final TodoistService todoistService;
    private final AgendaService agendaService;

    public TodoistController(TodoistService todoistService, AgendaService agendaService) {
        this.todoistService = todoistService;
        this.agendaService = agendaService;
    }

    @GetMapping("/estado")
    public Map<String, Object> estado() {
        return Map.of("configurado", todoistService.isEnabled());
    }

    /** Guarda (o borra, con apiKey vacia) la key. No la devuelve nunca. */
    @PostMapping("/config")
    public Map<String, Object> configurar(@RequestParam(required = false) String apiKey) {
        todoistService.guardarApiKey(apiKey);
        return Map.of("configurado", todoistService.isEnabled());
    }

    /** Prueba real contra Todoist: cuenta las tareas de la primera pagina. */
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

    /** Envia una tarea local a Todoist. Idempotente: no reenvia la ya enviada. */
    @PostMapping("/tareas/{id}")
    public ResponseEntity<?> enviarTarea(@PathVariable Integer id) {
        ItemAgenda t = agendaService.buscarPorId(id);
        if (!ItemAgenda.TAREA.equals(t.getTipo())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Solo se envian tareas a Todoist, no eventos"));
        }
        if (t.getTodoistId() != null && !t.getTodoistId().isBlank()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "La tarea ya esta en Todoist"));
        }
        if (!todoistService.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No hay API key de Todoist configurada"));
        }
        String remoto = todoistService.crearTarea(t.getTitulo(), t.getFecha(), t.getPrioridad());
        if (remoto == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Todoist no acepto la tarea (revisa la key o la conexion)"));
        }
        t.setTodoistId(remoto);
        agendaService.guardar(t);
        return ResponseEntity.ok(Map.of("ok", true, "todoistId", remoto));
    }
}
