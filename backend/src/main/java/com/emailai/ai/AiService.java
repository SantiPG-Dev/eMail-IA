package com.emailai.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.emailai.config.AppConfigStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;

// Servicio de IA unificado: LM Studio (API OpenAI-compatible) o directamente OpenAI.
// Reemplaza los 3 servicios legacy (IAService, IAAsistenteService, OllamaService)
// por una implementación simple con RestClient de Spring.
// baseUrl/modelo/prompt de chat son configurables desde la UI (claves ia.* de
// AppConfigStore); el @Value solo aporta los valores por defecto de arranque.
@Service
public class AiService {

    public static final String DEFAULT_BASE_URL = "http://localhost:1234";
    public static final String DEFAULT_MODEL = "qwen3.5:9b";
    public static final String DEFAULT_PROMPT = "Eres un asistente útil que responde en español.";

    // Volatile: se reconstruyen al guardar configuración (POST /api/ia/config)
    private volatile RestClient restClient;
    private volatile String baseUrl;
    private volatile String model;
    private final ObjectMapper mapper;
    private final int timeoutSeconds;
    // Nulo con el constructor corto (tests): sin overrides de AppConfigStore
    private final AppConfigStore config;

    @Autowired
    public AiService(
            @Value("${emailai.ai.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
            @Value("${emailai.ai.model:" + DEFAULT_MODEL + "}") String model,
            @Value("${emailai.ai.timeout:30}") int timeoutSeconds,
            AppConfigStore config) {
        this.config = config;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.mapper = new ObjectMapper();
        this.restClient = construirCliente(baseUrl, timeoutSeconds);
    }

    public AiService(String baseUrl, String model, int timeoutSeconds) {
        this(baseUrl, model, timeoutSeconds, null);
    }

    /** Arranca con la última configuración guardada desde la UI, si la hay. */
    @PostConstruct
    void aplicarConfigGuardada() {
        if (config == null) return;
        actualizar(
                config.get("ia.baseUrl", baseUrl),
                config.get("ia.model", model));
    }

    private static RestClient construirCliente(String baseUrl, int timeoutSeconds) {
        // Timeout REAL (connect + read): sin esto, una llamada a LM Studio
        // colgada bloquea el hilo indefinidamente aunque la config diga 30s.
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /** Reconstruye el cliente con otro servidor/modelo (POST /api/ia/config). */
    public synchronized void actualizar(String nuevoBaseUrl, String nuevoModelo) {
        if (nuevoBaseUrl == null || nuevoBaseUrl.isBlank()
                || nuevoModelo == null || nuevoModelo.isBlank()) {
            return;
        }
        this.baseUrl = nuevoBaseUrl.strip();
        this.model = nuevoModelo.strip();
        this.restClient = construirCliente(this.baseUrl, timeoutSeconds);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    /** Resultado de la prueba de conexión: modelos cargados en LM Studio. */
    public record EstadoIA(boolean ok, List<String> modelos, String error) {}

    /** Prueba un baseUrl sin guardarlo: devuelve los modelos que expone. */
    public EstadoIA probar(String baseUrlCandidato) {
        RestClient cliente = construirCliente(baseUrlCandidato, timeoutSeconds);
        try {
            String body = cliente.get().uri("/v1/models").retrieve().body(String.class);
            List<String> modelos = new ArrayList<>();
            for (JsonNode n : mapper.readTree(body).path("data")) {
                String id = n.path("id").asText("");
                if (!id.isEmpty()) modelos.add(id);
            }
            return new EstadoIA(true, modelos, null);
        } catch (Exception e) {
            return new EstadoIA(false, List.of(), e.getMessage());
        }
    }

    /**
     * Verifica que LM Studio responda.
     */
    public boolean isAvailable() {
        try {
            var res = restClient.get().uri("/v1/models").retrieve().toEntity(String.class);
            return res.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Genera un resumen breve del contenido de un correo.
     */
    public String generarResumen(String contenido) {
        if (contenido == null || contenido.length() < 200) {
            return contenido;
        }
        String prompt = "Resume el siguiente correo electrónico en una sola frase clara y directa en español:\n\n" + contenido;
        return chatString("Eres un asistente que resume correos.", prompt);
    }

    /**
     * Sugiere una respuesta profesional para un correo.
     */
    public String sugerirRespuesta(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            return "";
        }
        String prompt = "Basado en este correo, sugiere una respuesta corta y profesional en español:\n\n" + contenido;
        return chatString("Eres un asistente que ayuda a redactar respuestas profesionales.", prompt);
    }

    /**
     * Clasifica la prioridad de un correo usando IA.
     */
    public String clasificarPrioridad(String asunto, String contenido) {
        String prompt = String.format(
            "Clasifica la prioridad del siguiente email como ALTA, NORMAL o BAJA. " +
            "Responde solo con la palabra ALTA, NORMAL o BAJA.\n\nAsunto: %s\nContenido: %s",
            asunto != null ? asunto : "",
            contenido != null ? contenido : ""
        );
        return chatString("Clasificador de prioridad de correos.", prompt).trim();
    }

    /**
     * Chat conversacional con la IA: el system prompt es configurable desde
     * Configuración → IA (ia.prompt) para afinar el tono del asistente.
     */
    public String chat(String mensaje) {
        return chatString(promptDeChat(), mensaje);
    }

    private String promptDeChat() {
        return config != null
                ? config.get("ia.prompt", DEFAULT_PROMPT)
                : DEFAULT_PROMPT;
    }

    /**
     * Envía un mensaje a LM Studio y devuelve el contenido como texto.
     */
    private String chatString(String systemPrompt, String userMessage) {
        try {
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 512);

            ArrayNode messages = requestBody.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userMessage);

            String response = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            return "";
        }
    }
}
