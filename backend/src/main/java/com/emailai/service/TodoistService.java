package com.emailai.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.emailai.config.AppConfigStore;
import com.emailai.security.CredentialService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

// Integración opcional con Todoist (API unificada v1, la REST v2 se retiró en 2026).
// La API key se guarda cifrada en preferences.properties (clave todoist.apiKey)
// y se configura desde Ajustes → Integraciones; el @Value solo aporta el valor
// de arranque por entorno (EMAILAI_TODOIST_API_KEY).
@Service
public class TodoistService {

    private static final String TODOIST_API = "https://api.todoist.com/api/v1";

    private final ObjectMapper mapper = new ObjectMapper();
    private final AppConfigStore config;
    private final CredentialService credenciales;
    private final String apiKeyEntorno;

    public TodoistService(AppConfigStore config, CredentialService credenciales,
                          @Value("${emailai.todoist.api-key:}") String apiKey) {
        this.config = config;
        this.credenciales = credenciales;
        this.apiKeyEntorno = apiKey;
    }

    /** Key efectiva: la guardada (cifrada) o la del entorno. */
    private String apiKey() {
        String guardada = config.get("todoist.apiKey", "");
        if (!guardada.isBlank()) {
            String claro = credenciales.descifrar(guardada);
            return claro == null ? "" : claro.trim();
        }
        return apiKeyEntorno == null ? "" : apiKeyEntorno.trim();
    }

    public boolean isEnabled() { return !apiKey().isBlank(); }

    /** Guarda la key cifrada (cadena vacía la borra). */
    public void guardarApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            config.remove("todoist.apiKey");
        } else {
            config.put("todoist.apiKey", credenciales.cifrar(apiKey.trim()));
        }
    }

    private RestClient cliente() {
        return RestClient.builder()
                .baseUrl(TODOIST_API)
                .defaultHeader("Authorization", "Bearer " + apiKey())
                .build();
    }

    /**
     * Crea una tarea en Todoist. Devuelve el id remoto o null si falla.
     */
    public String crearTarea(String contenido, String fechaVencimiento, String prioridad) {
        if (!isEnabled()) return null;
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("content", contenido);
            if (fechaVencimiento != null && !fechaVencimiento.isBlank()) {
                body.put("due_date", fechaVencimiento);
            }
            if (prioridad != null) {
                body.put("priority", switch (prioridad.toUpperCase()) {
                    case "ALTA" -> 4;
                    case "MEDIA" -> 3;
                    case "BAJA" -> 2;
                    default -> 1;
                });
            }
            JsonNode res = cliente().post().uri("/tasks").body(body)
                    .retrieve().body(JsonNode.class);
            return res != null && res.hasNonNull("id") ? res.get("id").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Lista las tareas de Todoist (la v1 pagina: la primera página llega en results).
     * Lanza IllegalStateException si la API falla, para que la UI muestre el motivo.
     */
    public List<JsonNode> listarTareas() {
        if (!isEnabled()) return List.of();
        try {
            String res = cliente().get().uri("/tasks").retrieve().body(String.class);
            JsonNode raiz = mapper.readTree(res);
            JsonNode lista = raiz.isArray() ? raiz : raiz.path("results");
            return mapper.convertValue(lista,
                    mapper.getTypeFactory().constructCollectionType(List.class, JsonNode.class));
        } catch (Exception e) {
            throw new IllegalStateException(
                    e.getMessage() != null ? e.getMessage() : "Error llamando a Todoist", e);
        }
    }

    /**
     * Cierra una tarea en Todoist.
     */
    public boolean cerrarTarea(String taskId) {
        if (!isEnabled()) return false;
        try {
            var res = cliente().post().uri("/tasks/{id}/close", taskId)
                    .retrieve().toBodilessEntity();
            return res.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
