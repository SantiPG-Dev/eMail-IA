package com.emailai.web.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.emailai.security.CredentialService;
import com.emailai.service.CuentaService;
import com.emailai.service.MailService;

// Envío de correos vía SMTP.
@RestController
@RequestMapping("/api/enviar")
public class EnviarController {

    private final MailService mailService;
    private final CuentaService cuentaService;
    private final CredentialService credentialService;

    public EnviarController(MailService mailService, CuentaService cuentaService,
                            CredentialService credentialService) {
        this.mailService = mailService;
        this.cuentaService = cuentaService;
        this.credentialService = credentialService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> enviar(@RequestBody EnviarRequest req) {
        try {
            // Obtener credenciales de la cuenta default
            var cuentaOpt = cuentaService.buscarDefault();
            if (cuentaOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "No hay cuenta configurada",
                    "ok", false));
            }

            var cuenta = cuentaOpt.get();
            String servidor = cuenta.getServidor() != null
                ? cuenta.getServidor().replace("imap", "smtp")
                : "smtp.gmail.com";
            int puerto = servidor.contains("outlook") ? 587 : 465;
            String user = cuenta.getEmail();
            String pass = credentialService.descifrar(cuenta.getPasswordCifrada());

            if (pass == null || pass.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "La cuenta no tiene password configurado",
                    "ok", false));
            }

            boolean ok = mailService.enviarCorreo(servidor, puerto, user, pass,
                    req.para(), req.cc(), req.asunto(), req.cuerpo());

            return ResponseEntity.ok(Map.of(
                "ok", ok,
                "mensaje", ok ? "Correo enviado" : "Error al enviar"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "ok", false));
        }
    }

    public record EnviarRequest(String para, String cc, String asunto, String cuerpo) {}
}
