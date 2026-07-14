package com.emailai.web.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Properties;

import org.springframework.web.bind.annotation.*;

import com.emailai.domain.entities.Cuenta;
import com.emailai.repository.MensajeRepository;
import com.emailai.service.CuentaService;

import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.Store;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final MensajeRepository mensajeRepo;
    private final CuentaService cuentaService;

    public DebugController(MensajeRepository mensajeRepo, CuentaService cuentaService) {
        this.mensajeRepo = mensajeRepo;
        this.cuentaService = cuentaService;
    }

    @GetMapping("/mensajes")
    public Map<String, Object> contarMensajes() {
        long total = mensajeRepo.count();
        return Map.of(
            "totalEnBD", total,
            "mensajes", total > 0 ? mensajeRepo.findAll().stream().limit(3).map(m ->
                Map.of("id", m.getId(), "asunto", m.getAsunto(), "remitente", m.getRemitente(), "cuentaHash", m.getCuentaHash(), "categoria", m.getCategoria())
            ).toList() : java.util.List.of()
        );
    }

    @GetMapping("/test-imap")
    public Map<String, Object> testIMAP() {
        try {
            var cuentas = cuentaService.listarTodas();
            if (cuentas.isEmpty()) {
                return Map.of("ok", false, "error", "No hay cuentas configuradas");
            }
            Cuenta c = cuentas.get(0);
            String host = c.getServidor() != null ? c.getServidor() : "imap.gmail.com";
            String user = c.getEmail();
            String pass = c.getPasswordCifrada();

            Properties props = new Properties();
            props.put("mail.imaps.host", host);
            props.put("mail.imaps.port", "993");
            props.put("mail.imaps.ssl.enable", "true");
            props.put("mail.imaps.connectiontimeout", "10000");
            props.put("mail.imaps.timeout", "10000");

            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect(host, 993, user, pass);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            int total = inbox.getMessageCount();
            inbox.close(false);
            store.close();

            return Map.of("ok", true, "servidor", host, "usuario", user, "totalMensajes", total);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
