package com.emailai.web.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.emailai.domain.entities.Adjunto;
import com.emailai.domain.entities.Mensaje;
import com.emailai.domain.entities.Cuenta;
import com.emailai.repository.AdjuntoRepository;
import com.emailai.service.CredencialesMailService;
import com.emailai.service.CredencialesMailService.Credenciales;
import com.emailai.service.CuentaService;
import com.emailai.service.MailService;
import com.emailai.service.MensajeService;
import com.emailai.web.dto.AdjuntoResponse;
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
    private final CredencialesMailService credencialesMailService;
    private final AdjuntoRepository adjuntoRepository;

    public MensajeController(MensajeService mensajeService, MailService mailService,
                             CuentaService cuentaService,
                             CredencialesMailService credencialesMailService,
                             AdjuntoRepository adjuntoRepository) {
        this.mensajeService = mensajeService;
        this.mailService = mailService;
        this.cuentaService = cuentaService;
        this.credencialesMailService = credencialesMailService;
        this.adjuntoRepository = adjuntoRepository;
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
        int reclasificados = 0;

        if (categoria != null && !categoria.isBlank()) {
            // Feedback del usuario: forzar categoría + marcar remitente +
            // rescan de sus correos + entrenar Weka
            var res = mailService.forzarCategoria(m, categoria);
            m = res.mensaje();
            reclasificados = res.reclasificados();
        } else {
            // Clasificación automática con Weka
            m = mailService.clasificarMensaje(m);
        }

        mensajeService.guardarOActualizar(m);
        MensajeResponse resp = toResponse(m);
        if (reclasificados > 0) {
            resp = new MensajeResponse(resp.id(), resp.uid(), resp.cuentaHash(),
                    resp.carpetaImap(), resp.remitente(), resp.destinatarios(),
                    resp.cc(), resp.cco(), resp.asunto(), resp.cuerpo(), resp.html(),
                    resp.categoria(), resp.prioridad(), resp.fechaRecepcion(),
                    resp.adjuntos(), reclasificados);
        }
        return resp;
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

    /**
     * Resuelve host + credenciales (password u OAuth2 con refresh) de la
     * cuenta del mensaje. Nunca usa el email como password: si la cuenta
     * no tiene credenciales válidas, no se toca el servidor.
     */
    private record ContextoCuenta(String host, Credenciales cred, String tipoConexion) {}

    private ContextoCuenta contextoDe(String cuentaHash) {
        try {
            var cuenta = cuentaService.buscarPorEmail(cuentaHash);
            if (cuenta.isEmpty()) return null;
            Cuenta c = cuenta.get();
            String host = c.getServidor() != null ? c.getServidor()
                    : (c.getOauthProvider() != null || cuentaHash.contains("outlook")
                        ? "outlook.office365.com" : "imap.gmail.com");
            Credenciales cred = credencialesMailService.resolver(c);
            String tipo = c.getTipoConexion() != null ? c.getTipoConexion() : "IMAP";
            return new ContextoCuenta(host, cred, tipo);
        } catch (Exception e) {
            log.warn("Error resolviendo cuenta {}: {}", cuentaHash, e.getMessage());
            return null;
        }
    }

    /** Elimina del servidor (IMAP mueve a papelera, POP3 no aplica). */
    @DeleteMapping("/{id}/servidor")
    public ResponseEntity<String> eliminarDelServidor(@PathVariable Long id) {
        Mensaje m = mensajeService.buscarPorId(id);
        ContextoCuenta ctx = contextoDe(m.getCuentaHash());
        if (ctx == null || ctx.cred() == null) {
            return ResponseEntity.status(409).body(
                "La cuenta no tiene credenciales válidas (re-autentica OAuth o configura password)");
        }
        mailService.eliminarDelServidor(ctx.host(), ctx.cred().user(), ctx.cred().secret(),
                m.getCarpetaImap(), m.getUid(), ctx.tipoConexion(), ctx.cred().esOAuth());
        mensajeService.eliminar(id);
        log.info("AUDIT mensaje borrado del servidor id={} cuenta={} carpeta={}",
                id, m.getCuentaHash(), m.getCarpetaImap());
        return ResponseEntity.ok("Eliminado");
    }

    /** Mueve a otra carpeta (POP3 no soportado). */
    @PostMapping("/{id}/mover")
    public ResponseEntity<String> mover(@PathVariable Long id, @RequestParam String destino) {
        Mensaje m = mensajeService.buscarPorId(id);
        ContextoCuenta ctx = contextoDe(m.getCuentaHash());
        if (ctx == null || ctx.cred() == null) {
            return ResponseEntity.status(409).body(
                "La cuenta no tiene credenciales válidas (re-autentica OAuth o configura password)");
        }
        mailService.moverACarpeta(ctx.host(), ctx.cred().user(), ctx.cred().secret(),
                m.getCarpetaImap(), destino, m.getUid(), ctx.tipoConexion(), ctx.cred().esOAuth());
        return ResponseEntity.ok("Movido a " + destino);
    }

    private MensajeResponse toResponse(Mensaje m) {
        return new MensajeResponse(m.getId(), m.getUid(), m.getCuentaHash(),
                m.getCarpetaImap(), m.getRemitente(), m.getDestinatarios(),
                m.getCc(), m.getCco(), m.getAsunto(), m.getCuerpo(),
                m.getHtml(), m.getCategoria(), m.getPrioridad(), m.getFechaRecepcion(),
                m.getAdjuntos() != null
                    ? m.getAdjuntos().stream().map(this::adjuntoResponse).toList()
                    : List.of());
    }

    private AdjuntoResponse adjuntoResponse(Adjunto a) {
        return new AdjuntoResponse(a.getId(), a.getNombre(), a.getMimeType(), a.getTamanoBytes());
    }

    /**
     * Descarga un adjunto (metadatos + bytes). 404 si no pertenece al mensaje.
     */
    @GetMapping("/{id}/adjuntos/{adjuntoId}")
    public ResponseEntity<byte[]> descargarAdjunto(@PathVariable Long id,
                                                    @PathVariable Long adjuntoId) {
        var adjuntoOpt = adjuntoRepository.findByIdAndMensajeId(adjuntoId, id);
        if (adjuntoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Adjunto adj = adjuntoOpt.get();
        String nombre = java.net.URLEncoder.encode(adj.getNombre(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + nombre)
                .contentType(adj.getMimeType() != null
                        ? org.springframework.http.MediaType.parseMediaType(adj.getMimeType())
                        : org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(adj.getDatos());
    }
}
