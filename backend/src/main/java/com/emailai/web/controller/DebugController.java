package com.emailai.web.controller;

import java.util.Map;
import java.util.Properties;

import org.springframework.web.bind.annotation.*;

import com.emailai.domain.entities.Cuenta;
import com.emailai.security.CredentialService;
import com.emailai.repository.MensajeRepository;
import com.emailai.service.CuentaService;

import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.Store;

// Endpoints de depuración solo disponibles cuando emailai.debug.enabled=true.
// Por defecto el bean no se registra: nada de debug en producción.
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "emailai.debug.enabled", havingValue = "true", matchIfMissing = false)
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final MensajeRepository mensajeRepo;
    private final CuentaService cuentaService;
    private final CredentialService credentialService;

    public DebugController(MensajeRepository mensajeRepo, CuentaService cuentaService,
                           CredentialService credentialService) {
        this.mensajeRepo = mensajeRepo;
        this.cuentaService = cuentaService;
        this.credentialService = credentialService;
    }

    @GetMapping("/mensajes")
    public Map<String, Object> contarMensajes() {
        try {
            long total = mensajeRepo.count();
            var muestra = total > 0 ? mensajeRepo.findAll().stream().limit(3).map(m -> {
                try {
                    return Map.of(
                        "id", String.valueOf(m.getId()),
                        "asunto", m.getAsunto() != null ? m.getAsunto() : "(sin asunto)",
                        "remitente", m.getRemitente() != null ? m.getRemitente() : "(sin remitente)",
                        "cuentaHash", m.getCuentaHash() != null ? m.getCuentaHash() : "(sin cuenta)",
                        "categoria", m.getCategoria() != null ? m.getCategoria() : "DESCONOCIDO"
                    );
                } catch (Exception ex) {
                    return Map.of("id", String.valueOf(m.getId()), "error", ex.getMessage());
                }
            }).toList() : java.util.List.of();
            return Map.of("totalEnBD", total, "ok", true, "mensajes", muestra);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @GetMapping("/test-imap")
    public Object testIMAP() {
        try {
            var cuentas = cuentaService.listarTodas();
            if (cuentas.isEmpty()) {
                return Map.of("ok", false, "error", "No hay cuentas configuradas");
            }
            Cuenta c = cuentas.get(0);
            String host = c.getServidor() != null ? c.getServidor() : "imap.gmail.com";
            String user = c.getEmail();
            String pass = credentialService.descifrar(c.getPasswordCifrada());

            Properties props = new Properties();
            props.put("mail.imaps.host", host);
            props.put("mail.imaps.port", "993");
            props.put("mail.imaps.ssl.enable", "true");
            props.put("mail.imaps.connectiontimeout", "10000");
            props.put("mail.imaps.timeout", "10000");

            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect(host, 993, user, pass);

            // Listar carpetas y sus mensajes
            var carpetas = new java.util.ArrayList<Map<String, Object>>();
            Folder defaultFolder = store.getDefaultFolder();
            for (Folder f : defaultFolder.list("*")) {
                try {
                    f.open(Folder.READ_ONLY);
                    carpetas.add(Map.of(
                        "nombre", f.getFullName(),
                        "mensajes", f.getMessageCount(),
                        "noLeidos", f.getUnreadMessageCount()
                    ));
                    f.close(false);
                } catch (Exception ignored) {}
            }
            store.close();

            return Map.of(
                "ok", true, "servidor", host,
                "usuario", user, "carpetas", carpetas
            );
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
