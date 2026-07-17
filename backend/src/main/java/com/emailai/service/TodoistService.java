package com.emailai.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

// Integración opcional con Todoist API REST v2 para sincronizar tareas.
@Service
public class TodoistService {

    private static final String TODOIST_API = "https://api.todoist.com/rest/v2";

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final boolean enabled;

    public TodoistService(@Value("${emailai.todoist.api-key:}") String apiKey) {
        this.mapper = new ObjectMapper();
        this.enabled = apiKey != null && !apiKey.isBlank();
        if (this.enabled) {
            this.restClient = RestClient.builder()
                    .baseUrl(TODOIST_API)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build();
        } else {
            this.restClient = null;
        }
    }

    public boolean isEnabled() { return enabled; }

    /**
     * Crea una tarea en Todoist.
     */
    public boolean crearTarea(String contenido, String fechaVencimiento, String prioridad) {
        if (!enabled || restClient == null) return false;
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("content", contenido);
            if (fechaVencimiento != null && !fechaVencimiento.isBlank()) {
                body.put("due_string", fechaVencimiento);
            }
            if (prioridad != null) {
                body.put("priority", switch (prioridad.toUpperCase()) {
                    case "ALTA" -> 4;
                    case "MEDIA" -> 3;
                    case "BAJA" -> 2;
                    default -> 1;
                });
            }
            var res = restClient.post().uri("/tasks").body(body).retrieve().toBodilessEntity();
            return res.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Lista las tareas de Todoist.
     */
    public List<JsonNode> listarTareas() {
        if (!enabled || restClient == null) return List.of();
        try {
            String res = restClient.get().uri("/tasks").retrieve().body(String.class);
            return mapper.readValue(res, mapper.getTypeFactory().constructCollectionType(List.class, JsonNode.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Cierra una tarea en Todoist.
     */
    public boolean cerrarTarea(String taskId) {
        if (!enabled || restClient == null) return false;
        try {
            var res = restClient.post().uri("/tasks/{id}/close", taskId).retrieve().toBodilessEntity();
            return res.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
