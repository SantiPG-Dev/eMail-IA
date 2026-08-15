package com.emailai.web.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emailai.domain.entities.Cuenta;
import com.emailai.security.JwtService;
import com.emailai.security.LoginRateLimiter;
import com.emailai.security.SecureSessionManager;
import com.emailai.service.CuentaService;
import com.emailai.service.MailService;
import com.emailai.web.dto.LoginRequest;
import com.emailai.web.dto.LoginResponse;

// Autenticación con credenciales IMAP del correo.
// No hay contraseña maestra separada — tu password de email es tu password de la app.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final CuentaService cuentaService;
    private final MailService mailService;
    private final JwtService jwtService;
    private final SecureSessionManager sessionManager;
    private final LoginRateLimiter rateLimiter;

    public AuthController(CuentaService cuentaService, MailService mailService,
                          JwtService jwtService, SecureSessionManager sessionManager,
                          LoginRateLimiter rateLimiter) {
        this.cuentaService = cuentaService;
        this.mailService = mailService;
        this.jwtService = jwtService;
        this.sessionManager = sessionManager;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Estado de la app: si hay cuentas configuradas, se puede iniciar sesión.
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean hayCuentas = !cuentaService.listarTodas().isEmpty();
        return Map.of("configurada", hayCuentas, "sesionActiva", sessionManager.isActive());
    }

    /**
     * Inicia sesión verificando las credenciales IMAP contra el servidor de correo.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        String email = req.email();
        String password = req.masterPassword(); // reusamos el campo masterPassword para la contraseña IMAP

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email y contraseña obligatorios"));
        }

        // Rate limiting: cada intento abre una conexión IMAP real contra el
        // servidor de correo; sin límite, un bucle bloquea la cuenta.
        StringBuilder motivo = new StringBuilder();
        if (!rateLimiter.permitir(email, motivo)) {
            log.warn("AUDIT login bloqueado user={} motivo={}", email, motivo);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", motivo.toString()));
        }

        // Buscar cuenta por email
        Cuenta cuenta = cuentaService.buscarPorEmail(email).orElse(null);
        if (cuenta == null) {
            log.warn("AUDIT login fail user={} motivo=cuenta-no-existe", email);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No hay cuenta configurada para " + email));
        }

        // Verificar credenciales conectando al IMAP
        String servidor = cuenta.getServidor() != null ? cuenta.getServidor()
                : (email.contains("gmail") ? "imap.gmail.com"
                   : email.contains("outlook") || email.contains("hotmail") ? "outlook.office365.com"
                   : email.contains("gmx") ? "imap.gmx.com"
                   : "imap.gmail.com");
        int puerto = cuenta.getPuerto() != null ? cuenta.getPuerto() : 993;
        String tipoConexion = cuenta.getTipoConexion() != null ? cuenta.getTipoConexion() : "IMAP";

        try {
            mailService.probarConexion(servidor, puerto, email, password, tipoConexion);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.toUpperCase().contains("AUTHENTICATIONFAILED") || msg.contains("authentication failed")) {
                log.warn("AUDIT login fail user={} motivo=credenciales", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Contraseña incorrecta para " + email
                                + ". Si tienes 2FA, genera una contraseña específica para apps."));
            }
            log.warn("AUDIT login fail user={} motivo=servidor", email);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "No se pudo conectar al servidor " + servidor + ": " + msg));
        }

        // Actualizar contraseña almacenada (texto plano en H2, que ya está cifrada con cipher.key)
        cuenta.setPasswordCifrada(password);
        cuentaService.guardar(cuenta);

        // Iniciar sesión
        sessionManager.iniciarSesion();
        String token = jwtService.generateToken(email);
        rateLimiter.reset(email);

        log.info("AUDIT login ok user={}", email);
        return ResponseEntity.ok(new LoginResponse(token, email, true));
    }

    /**
     * Cierra la sesión actual.
     */
    @PostMapping("/logout")
    public Map<String, String> logout() {
        sessionManager.cerrarSesion();
        log.info("AUDIT logout");
        return Map.of("mensaje", "Sesión cerrada");
    }
}
