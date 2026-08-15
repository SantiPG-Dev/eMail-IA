package com.emailai.web.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.emailai.service.CredencialesMailService;
import com.emailai.service.CredencialesMailService.Credenciales;
import com.emailai.service.CuentaService;
import com.emailai.service.MailService;

// Envío de correos vía SMTP.
@RestController
@RequestMapping("/api/enviar")
public class EnviarController {

    private final MailService mailService;
    private final CuentaService cuentaService;
    private final CredencialesMailService credencialesMailService;

    public EnviarController(MailService mailService, CuentaService cuentaService,
                            CredencialesMailService credencialesMailService) {
        this.mailService = mailService;
        this.cuentaService = cuentaService;
        this.credencialesMailService = credencialesMailService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> enviar(@RequestBody EnviarRequest req) {
        try {
            // Obtener credenciales de la cuenta default (password u OAuth2 con XOAUTH2)
            var cuentaOpt = cuentaService.buscarDefault();
            if (cuentaOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "No hay cuenta configurada",
                    "ok", false));
            }

            var cuenta = cuentaOpt.get();
            Credenciales cred = credencialesMailService.resolver(cuenta);
            if (cred == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "La cuenta no tiene credenciales válidas (re-autentica OAuth o configura password)",
                    "ok", false));
            }

            // Host/puerto SMTP según proveedor; sustituye la antigua heurística
            // replace("imap","smtp") que rompía con outlook (no contiene "imap").
            String servidor;
            int puerto;
            if (cred.esOAuth()) {
                servidor = "MICROSOFT".equals(cuenta.getOauthProvider())
                        ? "smtp-mail.office.com" : "smtp.gmail.com";
                puerto = 587;
            } else if (cuenta.getServidor() != null && cuenta.getServidor().contains("imap")) {
                servidor = cuenta.getServidor().replace("imap", "smtp");
                puerto = 587;
            } else {
                servidor = cuenta.getServidor() != null ? cuenta.getServidor() : "smtp.gmail.com";
                puerto = 587;
            }
            boolean ok = mailService.enviarCorreo(servidor, puerto, cred.user(), cred.secret(),
                    req.para(), req.cc(), req.asunto(), req.cuerpo(), cred.esOAuth());

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
