package com.emailai.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.URLName;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.SearchTerm;

import com.emailai.ai.AiService;
import com.emailai.domain.entities.Mensaje;

/**
 * Servicio de correo IMAP/SMTP: conexión, sincronización, envío y
 * clasificación con IA (Weka) e IA generativa (LM Studio).
 *
 * <p>Portado del legacy MailService.java, adaptado a Spring + nuevos servicios.
 * Cada método de sincronización abre y cierra su propia conexión IMAP para
 * evitar sesiones stale y simplificar el lifecycle.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final MensajeService mensajeService;
    private final SpamIaService spamIaService;
    private final AiService aiService;
    private final RemitenteConfiableService remitenteService;

    public MailService(MensajeService mensajeService, SpamIaService spamIaService,
                       AiService aiService, RemitenteConfiableService remitenteService) {
        this.mensajeService = mensajeService;
        this.spamIaService = spamIaService;
        this.aiService = aiService;
        this.remitenteService = remitenteService;
    }

    // ── Conexión IMAP ──────────────────────────────────────────

    private Store conectarIMAP(String imapHost, String user, String password) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.imaps.host", imapHost);
        props.put("mail.imaps.port", "993");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "10000");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect(imapHost, 993, user, password);
        return store;
    }

    private Store conectarIMAPOAuth(String imapHost, String userEmail, String accessToken)
            throws MessagingException {
        Properties props = new Properties();
        props.put("mail.imaps.host", imapHost);
        props.put("mail.imaps.port", "993");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "10000");
        props.put("mail.imaps.auth.mechanisms", "XOAUTH2");
        props.put("mail.imaps.auth.login.disable", "true");
        props.put("mail.imaps.auth.plain.disable", "true");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect(imapHost, 993, userEmail, accessToken);
        return store;
    }

    // ── Carpetas ────────────────────────────────────────────────

    /**
     * Lista las carpetas IMAP disponibles.
     */
    public List<String> listarCarpetas(String imapHost, String user, String password)
            throws MessagingException {
        Store store = conectarIMAP(imapHost, user, password);
        try {
            Folder defaultFolder = store.getDefaultFolder();
            return Arrays.stream(defaultFolder.list("*"))
                    .map(Folder::getFullName)
                    .filter(n -> !n.contains("[Gmail]") || n.contains("INBOX"))
                    .sorted(Comparator.comparing(n -> !n.equals("INBOX")))
                    .toList();
        } finally {
            store.close();
        }
    }

    // ── Sincronizar bandeja ─────────────────────────────────────

    /**
     * Sincroniza los mensajes de una carpeta IMAP para una cuenta.
     * Guarda o actualiza cada mensaje en la BD local (upsert por UID).
     */
    public SyncResult sincronizarCarpeta(String imapHost, String user, String password,
                                           String cuentaHash, String carpetaImap)
            throws MessagingException, IOException {
        Store store = conectarIMAP(imapHost, user, password);
        try {
            Folder folder = store.getFolder(carpetaImap);
            folder.open(Folder.READ_ONLY);

            int total = folder.getMessageCount();
            int start = Math.max(1, total - 50); // últimos 50 mensajes
            Message[] msgs = folder.getMessages(start, total);

            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(FetchProfile.Item.CONTENT_INFO);
            fp.add(FetchProfile.Item.FLAGS);
            folder.fetch(msgs, fp);

            int nuevos = 0;
            for (Message msg : msgs) {
                Mensaje m = convertirMensaje(msg, cuentaHash, carpetaImap);
                if (m != null) {
                    mensajeService.guardarOActualizar(m);
                    nuevos++;
                }
            }

            folder.close(false);
            return new SyncResult(carpetaImap, nuevos, total);
        } finally {
            store.close();
        }
    }

    private Mensaje convertirMensaje(Message msg, String cuentaHash, String carpetaImap) {
        try {
            Mensaje m = new Mensaje();
            m.setUid(msg.getHeader("Message-ID") != null ? msg.getHeader("Message-ID")[0] : String.valueOf(System.nanoTime()));
            m.setCuentaHash(cuentaHash);
            m.setCarpetaImap(carpetaImap);

            if (msg.getFrom() != null && msg.getFrom().length > 0) {
                m.setRemitente(InternetAddress.toString(msg.getFrom()));
            }
            if (msg.getRecipients(jakarta.mail.Message.RecipientType.TO) != null) {
                m.setDestinatarios(InternetAddress.toString(msg.getRecipients(jakarta.mail.Message.RecipientType.TO)));
            }
            if (msg.getRecipients(jakarta.mail.Message.RecipientType.CC) != null) {
                m.setCc(InternetAddress.toString(msg.getRecipients(jakarta.mail.Message.RecipientType.CC)));
            }
            m.setAsunto(msg.getSubject());
            m.setFechaRecepcion(msg.getReceivedDate() != null
                    ? msg.getReceivedDate().toInstant().toString()
                    : (msg.getSentDate() != null ? msg.getSentDate().toInstant().toString() : ""));

            // Extraer cuerpo y HTML
            if (msg.isMimeType("text/plain")) {
                m.setCuerpo((String) msg.getContent());
            } else if (msg.isMimeType("text/html")) {
                m.setHtml((String) msg.getContent());
            } else if (msg.getContent() instanceof MimeMultipart multipart) {
                extraerPartes(multipart, m);
            }

            return m;
        } catch (Exception e) {
            log.warn("Error convirtiendo mensaje IMAP: {}", e.getMessage());
            return null;
        }
    }

    private void extraerPartes(MimeMultipart multipart, Mensaje m) throws MessagingException, IOException {
        for (int i = 0; i < multipart.getCount(); i++) {
            var part = multipart.getBodyPart(i);
            if (part.isMimeType("text/plain") && m.getCuerpo() == null) {
                m.setCuerpo((String) part.getContent());
            } else if (part.isMimeType("text/html") && m.getHtml() == null) {
                m.setHtml((String) part.getContent());
            } else if (part.getContent() instanceof MimeMultipart nested) {
                extraerPartes(nested, m);
            }
        }
    }

    // ── Clasificar con Weka ─────────────────────────────────────

    /**
     * Clasifica un mensaje como LEGITIMO/SPAM/PHISHING usando Weka
     * y actualiza la categoria en BD.
     */
    public Mensaje clasificarMensaje(Mensaje mensaje) {
        if (mensaje.getCategoria() != null && !"DESCONOCIDO".equals(mensaje.getCategoria())) {
            return mensaje; // ya clasificado
        }

        try {
            // Primero comprobar lista blanca
            if (mensaje.getRemitente() != null && remitenteService.esConfiable(mensaje.getRemitente())) {
                mensaje.setCategoria("LEGITIMO");
                return mensaje;
            }

            // Clasificar con Weka
            SpamIaService.ClaseCorreo clase = spamIaService.clasificar(mensaje.getCuentaHash(), mensaje);
            mensaje.setCategoria(clase.name());
        } catch (Exception e) {
            log.warn("Error clasificando mensaje: {}", e.getMessage());
            mensaje.setCategoria("DESCONOCIDO");
        }
        return mensaje;
    }

    /**
     * Reentrena el modelo Weka con los mensajes clasificados de una cuenta.
     */
    public void reentrenarModelo(String cuentaHash) {
        try {
            // Tomar todos los mensajes clasificados de la cuenta
            var mensajes = mensajeService.listarPorCarpeta(cuentaHash, "INBOX");
            var entrenamiento = mensajes.stream()
                    .filter(m -> m.getCategoria() != null && !"DESCONOCIDO".equals(m.getCategoria()))
                    .toList();

            if (entrenamiento.size() >= 5) {
                spamIaService.entrenarModelo(cuentaHash, entrenamiento);
                log.info("Modelo reentrenado para cuenta {} con {} ejemplos", cuentaHash, entrenamiento.size());
            }
        } catch (Exception e) {
            log.error("Error reentrenando modelo: {}", e.getMessage());
        }
    }

    // ── Resumen IA ──────────────────────────────────────────────

    /**
     * Genera un resumen del contenido del mensaje usando LM Studio.
     */
    public String generarResumen(Mensaje mensaje) {
        String contenido = mensaje.getCuerpo() != null ? mensaje.getCuerpo() : mensaje.getHtml();
        return aiService.generarResumen(contenido != null ? contenido : "");
    }

    /**
     * Sugiere una respuesta profesional usando LM Studio.
     */
    public String sugerirRespuesta(Mensaje mensaje) {
        String contenido = mensaje.getCuerpo() != null ? mensaje.getCuerpo() : mensaje.getHtml();
        return aiService.sugerirRespuesta(contenido != null ? contenido : "");
    }

    // ── Sincronización completa ─────────────────────────────────

    /**
     * Sincroniza todas las carpetas de una cuenta.
     */
    public List<SyncResult> sincronizarTodo(String imapHost, String user, String password,
                                             String cuentaHash) throws MessagingException, IOException {
        List<String> carpetas = listarCarpetas(imapHost, user, password);
        List<SyncResult> resultados = new ArrayList<>();
        for (String carpeta : carpetas) {
            resultados.add(sincronizarCarpeta(imapHost, user, password, cuentaHash, carpeta));
        }
        // Reentrenar modelo tras sincronizar
        reentrenarModelo(cuentaHash);
        // Limpiar mensajes antiguos
        for (String carpeta : carpetas) {
            mensajeService.limpiarAntiguos(cuentaHash, carpeta);
        }
        return resultados;
    }

    // ── Resultado ───────────────────────────────────────────────

    /** Resultado de sincronizar una carpeta. */
    public record SyncResult(String carpeta, int nuevos, int total) {}
}
