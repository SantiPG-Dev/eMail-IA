package com.emailai.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Servicio de IA unificado que consulta LM Studio (API OpenAI-compatible)
 * o directamente OpenAI, para resumir correos, sugerir respuestas,
 * clasificar prioridad y mantener conversaciones.
 *
 * <p>Reemplaza los servicios legacy IAService, IAAsistenteService y
 * OllamaService (que usaban LangChain4j) por una implementación más
 * simple basada en {@link RestClient} de Spring, siguiendo el patrón
 * de EazyPlanIA.
 */
@Service
public class AiService {

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String model;
    private final int timeoutSeconds;

    public AiService(
            @Value("${emailai.ai.base-url:http://localhost:1234}") String baseUrl,
            @Value("${emailai.ai.model:qwen3.5:9b}") String model,
            @Value("${emailai.ai.timeout:30}") int timeoutSeconds) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.mapper = new ObjectMapper();
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
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
     * Chat conversacional con la IA.
     */
    public String chat(String mensaje) {
        return chatString("Eres un asistente útil que responde en español.", mensaje);
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
