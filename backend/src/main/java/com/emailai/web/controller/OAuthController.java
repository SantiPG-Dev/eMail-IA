package com.emailai.web.controller;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.emailai.oauth.OAuthService;
import com.emailai.oauth.OAuthSession;

// Inicio de flujo OAuth2 y creación de cuenta con los tokens obtenidos.
// Las credenciales OAuth (clientId, clientSecret) están en el backend,
// no viajan desde el frontend.
@RestController
@RequestMapping("/api/oauth")
public class OAuthController {

    private final OAuthService oauthService;

    public OAuthController(OAuthService oauthService) {
        this.oauthService = oauthService;
    }

    /**
     * Inicia el flujo OAuth de forma asíncrona: arranca la escucha del
     * callback en background y devuelve inmediatamente el id del flujo y
     * la URL de autorización. El resultado se consulta con GET /estado/{id}.
     */
    @PostMapping("/iniciar")
    public ResponseEntity<Map<String, String>> iniciar(@RequestParam String proveedor) {
        try {
            var flujo = oauthService.iniciarFlujoAsync(proveedor);
            return ResponseEntity.ok(Map.of("flujoId", flujo.flujoId(), "authUrl", flujo.authUrl()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            // Ya hay un flujo en curso (puerto de callback único)
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Estado del flujo OAuth: PENDIENTE | COMPLETADO | TIMEOUT | ERROR.
     * En COMPLETADO incluye los tokens; en TIMEOUT/ERROR el motivo.
     */
    @GetMapping("/estado/{flujoId}")
    public ResponseEntity<?> estado(@PathVariable String flujoId) {
        try {
            var e = oauthService.estadoFlujo(flujoId);
            return switch (e.estado()) {
                case OAuthService.FLUJO_COMPLETADO -> ResponseEntity.ok(e.session());
                case OAuthService.FLUJO_PENDIENTE -> ResponseEntity.ok(Map.of("estado", e.estado()));
                case OAuthService.FLUJO_TIMEOUT -> ResponseEntity.status(408).body(Map.of(
                    "estado", e.estado(),
                    "error", "Tiempo de espera agotado. No se recibió la autorización en 2 minutos."));
                case OAuthService.FLUJO_ERROR -> ResponseEntity.badRequest().body(Map.of(
                    "estado", e.estado(),
                    "error", "Error en flujo OAuth: " + e.error()));
                default -> ResponseEntity.internalServerError().body(Map.of("error", "estado desconocido"));
            };
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
}
