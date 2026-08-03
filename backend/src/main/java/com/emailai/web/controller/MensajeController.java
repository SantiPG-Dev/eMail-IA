package com.emailai.web.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.emailai.domain.entities.Mensaje;
import com.emailai.security.CredentialService;
import com.emailai.service.CuentaService;
import com.emailai.service.MailService;
import com.emailai.service.MensajeService;
import com.emailai.web.dto.MensajeListResponse;
import com.emailai.web.dto.MensajeResponse;

// CRUD de mensajes + sincronización, clasificación, resumen IA.
@RestController
@RequestMapping("/api/mensajes")
public class MensajeController {

    private static final Logger log = LoggerFactory.getLogger(MensajeController.class);

    private final MensajeService mensajeService;
    private final MailService mailService;
    private final CuentaService cuentaService;
    private final CredentialService credentialService;

    public MensajeController(MensajeService mensajeService, MailService mailService,
                             CuentaService cuentaService,
                             CredentialService credentialService) {
        this.mensajeService = mensajeService;
        this.mailService = mailService;
        this.cuentaService = cuentaService;
        this.credentialService = credentialService;
    }

    @GetMapping
    public MensajeListResponse listar(
            @RequestParam String cuentaHash,
            @RequestParam(defaultValue = "INBOX") String carpeta,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limite) {
        var lista = mensajeService.listarPaginado(cuentaHash, carpeta, offset, limite);
        long total = mensajeService.contar(cuentaHash, carpeta);
        return new MensajeListResponse(
                lista.stream().map(this::toResponse).toList(),
                total, offset / limite, limite);
    }

    @GetMapping("/buscar")
    public MensajeListResponse buscar(
            @RequestParam String cuentaHash,
            @RequestParam(defaultValue = "INBOX") String carpeta,
            @RequestParam String q) {
        var lista = mensajeService.buscar(cuentaHash, carpeta, q);
        return new MensajeListResponse(
                lista.stream().map(this::toResponse).toList(),
                lista.size(), 0, lista.size());
    }

    @GetMapping("/{id}")
    public MensajeResponse obtener(@PathVariable Long id) {
        return toResponse(mensajeService.buscarPorId(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        mensajeService.eliminar(id);
        log.info("AUDIT mensaje borrado (local) id={}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Clasifica un mensaje.
     *
     * @param id       ID del mensaje
     * @param categoria (opcional) Si se proporciona (SPAM/LEGITIMO/PHISHING),
     *                  fuerza esa categoría (feedback del usuario) y reentrena Weka.
     *                  Si no se proporciona, ejecuta la clasificación automática con Weka.
     */
    @PostMapping("/{id}/clasificar")
    public MensajeResponse clasificar(
            @PathVariable Long id,
            @RequestParam(required = false) String categoria) {
        Mensaje m = mensajeService.buscarPorId(id);

        if (categoria != null && !categoria.isBlank()) {
            // Feedback del usuario: forzar categoría + entrenar Weka
            m = mailService.forzarCategoria(m, categoria);
        } else {
            // Clasificación automática con Weka
            m = mailService.clasificarMensaje(m);
        }

        mensajeService.guardarOActualizar(m);
        return toResponse(m);
    }

    @PostMapping("/{id}/resumen")
    public String resumen(@PathVariable Long id) {
        Mensaje m = mensajeService.buscarPorId(id);
        return mailService.generarResumen(m);
    }

    @PostMapping("/{id}/sugerir")
    public String sugerir(@PathVariable Long id) {
        Mensaje m = mensajeService.buscarPorId(id);
        return mailService.sugerirRespuesta(m);
    }

    // ── Acciones IMAP ───────────────────────────────────────────

    private String getPasswordFromCuenta(String cuentaHash) {
        try {
            var cuenta = cuentaService.buscarPorEmail(cuentaHash);
            if (cuenta.isPresent()) {
                String pass = credentialService.descifrar(cuenta.get().getPasswordCifrada());
                if (pass != null && !pass.isBlank()) return pass;
                String token = credentialService.descifrar(cuenta.get().getOauthAccessToken());
                if (token != null && !token.isBlank()) return token;
            }
        } catch (Exception ignored) {}
        return cuentaHash;
    }

    private String getImapHost(String cuentaHash) {
        try {
            var cuenta = cuentaService.buscarPorEmail(cuentaHash);
            if (cuenta.isPresent() && cuenta.get().getServidor() != null)
                return cuenta.get().getServidor();
        } catch (Exception ignored) {}
        return cuentaHash != null && cuentaHash.contains("outlook")
                ? "outlook.office365.com" : "imap.gmail.com";
    }

    private String getTipoConexion(String cuentaHash) {
        try {
            var cuenta = cuentaService.buscarPorEmail(cuentaHash);
            if (cuenta.isPresent() && cuenta.get().getTipoConexion() != null)
                return cuenta.get().getTipoConexion();
        } catch (Exception ignored) {}
        return "IMAP";
    }

    /** Elimina del servidor (IMAP mueve a papelera, POP3 no aplica). */
    @DeleteMapping("/{id}/servidor")
    public String eliminarDelServidor(@PathVariable Long id) {
        Mensaje m = mensajeService.buscarPorId(id);
        String pass = getPasswordFromCuenta(m.getCuentaHash());
        String host = getImapHost(m.getCuentaHash());
        String tipo = getTipoConexion(m.getCuentaHash());
        mailService.eliminarDelServidor(host, m.getCuentaHash(), pass,
                m.getCarpetaImap(), m.getUid(), tipo);
        mensajeService.eliminar(id);
        log.info("AUDIT mensaje borrado del servidor id={} cuenta={} carpeta={}",
                id, m.getCuentaHash(), m.getCarpetaImap());
        return "Eliminado";
    }

    /** Mueve a otra carpeta (POP3 no soportado). */
    @PostMapping("/{id}/mover")
    public String mover(@PathVariable Long id, @RequestParam String destino) {
        Mensaje m = mensajeService.buscarPorId(id);
        String pass = getPasswordFromCuenta(m.getCuentaHash());
        String host = getImapHost(m.getCuentaHash());
        String tipo = getTipoConexion(m.getCuentaHash());
        mailService.moverACarpeta(host, m.getCuentaHash(), pass,
                m.getCarpetaImap(), destino, m.getUid(), tipo);
        return "Movido a " + destino;
    }

    private MensajeResponse toResponse(Mensaje m) {
        return new MensajeResponse(m.getId(), m.getUid(), m.getCuentaHash(),
                m.getCarpetaImap(), m.getRemitente(), m.getDestinatarios(),
                m.getCc(), m.getCco(), m.getAsunto(), m.getCuerpo(),
                m.getHtml(), m.getCategoria(), m.getPrioridad(), m.getFechaRecepcion());
    }
}
