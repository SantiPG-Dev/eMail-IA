package com.emailai.web.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.emailai.repository.MensajeRepository;

/**
 * Endpoints de depuración para verificar el estado de la BD.
 */
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final MensajeRepository mensajeRepo;

    public DebugController(MensajeRepository mensajeRepo) {
        this.mensajeRepo = mensajeRepo;
    }

    @GetMapping("/mensajes")
    public Map<String, Object> contarMensajes() {
        long total = mensajeRepo.count();
        return Map.of(
            "totalEnBD", total,
            "mensajes", total > 0 ? mensajeRepo.findAll().stream().limit(3).map(m ->
                Map.of("id", m.getId(), "asunto", m.getAsunto(), "remitente", m.getRemitente(), "cuentaHash", m.getCuentaHash())
            ).toList() : java.util.List.of()
        );
    }
}
