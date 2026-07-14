package com.emailai.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.emailai.ai.AiService;
import com.emailai.domain.entities.Mensaje;
import com.emailai.service.MailService;
import com.emailai.service.MensajeService;
import com.emailai.web.dto.IAChatRequest;
import com.emailai.web.dto.IAChatResponse;

@RestController
@RequestMapping("/api/ia")
public class IAController {

    private final AiService aiService;
    private final MailService mailService;
    private final MensajeService mensajeService;

    public IAController(AiService aiService, MailService mailService, MensajeService mensajeService) {
        this.aiService = aiService;
        this.mailService = mailService;
        this.mensajeService = mensajeService;
    }

    @GetMapping("/status")
    public IAChatResponse status() {
        return new IAChatResponse("IA " + (aiService.isAvailable() ? "disponible" : "no disponible"),
                aiService.isAvailable());
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
