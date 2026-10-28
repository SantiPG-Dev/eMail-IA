package com.emailai.web.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.emailai.domain.entities.RemitenteConfiable;
import com.emailai.service.SpamIaService;
import com.emailai.service.RemitenteConfiableService;

// Lista de remitentes clasificados a mano (LEGITIMO / SPAM / PHISHING).
// La clasificación la consulta antes que el modelo Weka.
@RestController
@RequestMapping("/api/remitentes")
public class RemitenteController {

    private final RemitenteConfiableService service;

    public RemitenteController(RemitenteConfiableService service) {
        this.service = service;
    }

    public record RemitenteRequest(
            @NotBlank @Email String remitente,
            @Pattern(regexp = "LEGITIMO|SPAM|PHISHING",
                     message = "categoría válida: LEGITIMO, SPAM o PHISHING") String categoria) {}

    @GetMapping
    public List<Map<String, String>> listar() {
        return service.listarRegistros().stream()
                .map(this::toMap)
                .toList();
    }

    @PostMapping
    public ResponseEntity<?> guardar(@Valid @RequestBody RemitenteRequest req) {
        String cat = req.categoria() != null ? req.categoria().toUpperCase() : "LEGITIMO";
        if (!SpamIaService.esClaseValida(cat)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Categoría inválida: " + cat));
        }
        service.agregar(req.remitente(), cat);
        return ResponseEntity.ok(Map.of("ok", true, "remitente", req.remitente(), "categoria", cat));
    }

    @DeleteMapping
    public ResponseEntity<Void> eliminar(@RequestParam String remitente) {
        service.eliminar(remitente);
        return ResponseEntity.noContent().build();
    }

    private Map<String, String> toMap(RemitenteConfiable r) {
        return Map.of("remitente", r.getRemitente(), "categoria", r.getCategoria());
    }
}
