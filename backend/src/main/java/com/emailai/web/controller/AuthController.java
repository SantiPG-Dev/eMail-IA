package com.emailai.web.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.emailai.config.AppConfigStore;
import com.emailai.security.JwtService;
import com.emailai.security.SecureSessionManager;
import com.emailai.security.SecureStorage;
import com.emailai.web.dto.LoginRequest;
import com.emailai.web.dto.LoginResponse;

/**
 * Controlador de autenticación con contraseña maestra.
 *
 * <p>Flujo:
 * <ol>
 *   <li><b>Primera ejecución:</b> {@code POST /api/auth/setup} — crea el hash PBKDF2
 *       de la contraseña maestra y lo guarda en AppConfigStore.</li>
 *   <li><b>Ejecuciones siguientes:</b> {@code POST /api/auth/login} — verifica la
 *       contraseña contra el hash, si es correcta devuelve un JWT de sesión
 *       y deriva la clave para SecureSessionManager.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String PREF_HASH = "master_password_hash";

    private final AppConfigStore configStore;
    private final JwtService jwtService;
    private final SecureSessionManager sessionManager;

    public AuthController(AppConfigStore configStore, JwtService jwtService,
                          SecureSessionManager sessionManager) {
        this.configStore = configStore;
        this.jwtService = jwtService;
        this.sessionManager = sessionManager;
    }

    /**
     * Comprueba si ya existe una contraseña maestra configurada.
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean configurada = configStore.containsKey(PREF_HASH);
        return Map.of("configurada", configurada, "sesionActiva", sessionManager.isActive());
    }

    /**
     * Configura la contraseña maestra (solo primera ejecución).
     */
    @PostMapping("/setup")
    public ResponseEntity<Map<String, String>> setup(@RequestBody LoginRequest req) {
        if (configStore.containsKey(PREF_HASH)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "La contraseña maestra ya está configurada"));
        }

        String hash = SecureStorage.hashear(req.masterPassword());
        configStore.put(PREF_HASH, hash);

        return ResponseEntity.ok(Map.of("mensaje", "Contraseña maestra configurada correctamente"));
    }

    /**
     * Inicia sesión con la contraseña maestra.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        String storedHash = configStore.get(PREF_HASH, null);
        if (storedHash == null) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(Map.of("error", "No hay contraseña configurada. Usa /api/auth/setup primero"));
        }

        if (!SecureStorage.verificarHash(req.masterPassword(), storedHash)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Contraseña incorrecta"));
        }

        // Derivar clave para sesión (PBKDF2 con la misma contraseña)
        sessionManager.iniciarSesion(req.masterPassword());

        // Generar JWT
        String token = jwtService.generateToken("emailai-user");
        return ResponseEntity.ok(new LoginResponse(token, "local", true));
    }

    /**
     * Cierra la sesión actual.
     */
    @PostMapping("/logout")
    public Map<String, String> logout() {
        sessionManager.cerrarSesion();
        return Map.of("mensaje", "Sesión cerrada");
    }
}
