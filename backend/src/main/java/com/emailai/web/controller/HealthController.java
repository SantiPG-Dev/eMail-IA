package com.emailai.web.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de salud para que Electron verifique que el backend está listo.
 *
 * <p>Electron hace polling a {@code GET /health} tras lanzar el JAR y antes
 * de cargar la ventana del navegador.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("app", "eMail-IA Backend");
        return status;
    }
}
