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
     * Inicia el flujo OAuth: devuelve la URL de autorización para abrir en el navegador.
     */
    @PostMapping("/iniciar")
    public ResponseEntity<Map<String, String>> iniciar(@RequestParam String proveedor) {
        try {
            String authUrl = oauthService.iniciarFlujo(proveedor);
            return ResponseEntity.ok(Map.of("authUrl", authUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Espera el callback OAuth y devuelve los tokens obtenidos.
     * El frontend debe llamar a este endpoint justo después de abrir la URL de auth.
     */
    @PostMapping("/callback")
    public ResponseEntity<?> esperarCallback(@RequestParam String proveedor) {
        try {
            OAuthSession session = oauthService.esperarCallback(proveedor, 120);
            return ResponseEntity.ok(session);
        } catch (java.util.concurrent.TimeoutException e) {
            return ResponseEntity.status(408).body(Map.of(
                "error", "Tiempo de espera agotado. No se recibió la autorización en 2 minutos."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Error en flujo OAuth: " + e.getMessage()
            ));
        }
    }
}
