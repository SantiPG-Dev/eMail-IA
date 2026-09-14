package com.emailai.web.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.emailai.ai.AiService;
import com.emailai.config.AppConfigStore;
import com.emailai.domain.entities.Mensaje;
import com.emailai.service.MailService;
import com.emailai.service.MensajeService;
import com.emailai.web.dto.IAChatRequest;
import com.emailai.web.dto.IAChatResponse;

// Chat con IA, resumen de correos y sugerencias de respuesta.
// /config y /conectar gestionan la conexión a LM Studio desde la UI.
@RestController
@RequestMapping("/api/ia")
public class IAController {

    private final AiService aiService;
    private final MailService mailService;
    private final MensajeService mensajeService;
    private final AppConfigStore configStore;

    public IAController(AiService aiService, MailService mailService, MensajeService mensajeService,
                        AppConfigStore configStore) {
        this.aiService = aiService;
        this.mailService = mailService;
        this.mensajeService = mensajeService;
        this.configStore = configStore;
    }

    @GetMapping("/status")
    public IAChatResponse status() {
        return new IAChatResponse("IA " + (aiService.isAvailable() ? "disponible" : "no disponible"),
                aiService.isAvailable());
    }

    /** Configuración actual de la conexión (claves ia.* en preferences.properties). */
    @GetMapping("/config")
    public Map<String, String> config() {
        return Map.of(
                "baseUrl", configStore.get("ia.baseUrl", AiService.DEFAULT_BASE_URL),
                "model", configStore.get("ia.model", AiService.DEFAULT_MODEL),
                "prompt", configStore.get("ia.prompt", AiService.DEFAULT_PROMPT));
    }

    /** Guarda servidor/modelo/prompt y reconstruye el cliente al momento. */
    @PostMapping("/config")
    public Map<String, String> guardarConfig(@RequestParam String baseUrl,
                                             @RequestParam String model,
                                             @RequestParam String prompt) {
        configStore.put("ia.baseUrl", baseUrl);
        configStore.put("ia.model", model);
        configStore.put("ia.prompt", prompt);
        aiService.actualizar(baseUrl, model);
        return Map.of("status", "ok");
    }

    /** Prueba de conexión (guardada o candidata): lista los modelos expuestos. */
    @PostMapping("/conectar")
    public AiService.EstadoIA conectar(@RequestParam(required = false) String baseUrl) {
        String destino = (baseUrl == null || baseUrl.isBlank())
                ? configStore.get("ia.baseUrl", AiService.DEFAULT_BASE_URL)
                : baseUrl;
        return aiService.probar(destino);
    }

    @PostMapping("/chat")
    public IAChatResponse chat(@RequestBody IAChatRequest req) {
        if ("resumir".equals(req.tipo()) && req.mensajeId() != null) {
            Mensaje m = mensajeService.buscarPorId(req.mensajeId());
            return new IAChatResponse(mailService.generarResumen(m), aiService.isAvailable());
        }
        if ("sugerir".equals(req.tipo()) && req.mensajeId() != null) {
            Mensaje m = mensajeService.buscarPorId(req.mensajeId());
            return new IAChatResponse(mailService.sugerirRespuesta(m), aiService.isAvailable());
        }
        return new IAChatResponse(aiService.chat(req.mensaje()), aiService.isAvailable());
    }

    @PostMapping("/reentrenar")
    public String reentrenar(@RequestParam String cuentaHash) {
        mailService.reentrenarModelo(cuentaHash);
        return "Reentrenamiento iniciado para cuenta: " + cuentaHash;
    }
}
