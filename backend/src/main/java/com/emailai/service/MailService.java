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
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
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
        return sincronizarCarpeta(imapHost, user, password, cuentaHash, carpetaImap, 300);
    }

    public SyncResult sincronizarCarpeta(String imapHost, String user, String password,
                                           String cuentaHash, String carpetaImap, int maxSync)
            throws MessagingException, IOException {
        Store store = conectarIMAP(imapHost, user, password);
        try {
            Folder folder = store.getFolder(carpetaImap);
            folder.open(Folder.READ_ONLY);

            int total = folder.getMessageCount();
            int start = Math.max(1, total - maxSync);
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
        return sincronizarTodo(imapHost, user, password, cuentaHash, 300);
    }

    public List<SyncResult> sincronizarTodo(String imapHost, String user, String password,
                                             String cuentaHash, int maxSync)
            throws MessagingException, IOException {
        List<String> carpetas = listarCarpetas(imapHost, user, password);
        List<SyncResult> resultados = new ArrayList<>();
        for (String carpeta : carpetas) {
            resultados.add(sincronizarCarpeta(imapHost, user, password, cuentaHash, carpeta, maxSync));
        }
        // Reentrenar modelo tras sincronizar
        reentrenarModelo(cuentaHash);
        // Limpiar mensajes antiguos
        for (String carpeta : carpetas) {
            mensajeService.limpiarAntiguos(cuentaHash, carpeta);
        }
        return resultados;
    }

    // ── Acciones IMAP ───────────────────────────────────────────

    /**
     * Elimina un mensaje del servidor IMAP (mueve a papelera/borrados).
     */
    public boolean eliminarDelServidor(String imapHost, String user, String password,
                                        String carpetaOrigen, String uid) {
        try {
            Store store = conectarIMAP(imapHost, user, password);
            try {
                Folder folder = store.getFolder(carpetaOrigen);
                folder.open(Folder.READ_WRITE);
                Message[] msgs = folder.getMessages();
                for (Message msg : msgs) {
                    String[] mid = msg.getHeader("Message-ID");
                    if (mid != null && mid[0].equals(uid)) {
                        msg.setFlag(Flags.Flag.DELETED, true);
                        folder.expunge();
                        return true;
                    }
                }
                folder.close(true);
            } finally {
                store.close();
            }
        } catch (Exception e) {
            log.warn("Error eliminando mensaje del servidor: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Mueve un mensaje a otra carpeta (ej: SPAM, Papelera).
     */
    public boolean moverACarpeta(String imapHost, String user, String password,
                                  String carpetaOrigen, String carpetaDestino, String uid) {
        try {
            Store store = conectarIMAP(imapHost, user, password);
            try {
                Folder origen = store.getFolder(carpetaOrigen);
                Folder destino = store.getFolder(carpetaDestino);
                if (!destino.exists()) destino.create(Folder.HOLDS_MESSAGES);

                origen.open(Folder.READ_WRITE);
                Message[] msgs = origen.getMessages();
                for (Message msg : msgs) {
                    String[] mid = msg.getHeader("Message-ID");
                    if (mid != null && mid[0].equals(uid)) {
                        origen.copyMessages(new Message[]{msg}, destino);
                        msg.setFlag(Flags.Flag.DELETED, true);
                        origen.expunge();
                        return true;
                    }
                }
                origen.close(true);
            } finally {
                store.close();
            }
        } catch (Exception e) {
            log.warn("Error moviendo mensaje: {}", e.getMessage());
        }
        return false;
    }

    // ── Envio SMTP ──────────────────────────────────────────────

    /**
     * Envia un correo via SMTP.
     */
    public boolean enviarCorreo(String smtpHost, int smtpPort, String user, String password,
                                 String to, String cc, String subject, String body) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", smtpPort);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");

            Session session = Session.getInstance(props, new jakarta.mail.Authenticator() {
                @Override
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new jakarta.mail.PasswordAuthentication(user, password);
                }
            });

            var msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(user));
            msg.setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse(to));
            if (cc != null && !cc.isBlank()) {
                msg.setRecipients(jakarta.mail.Message.RecipientType.CC, InternetAddress.parse(cc));
            }
            msg.setSubject(subject);
            msg.setText(body);
            msg.setSentDate(new java.util.Date());

            Transport.send(msg);
            log.info("Correo enviado a {} desde {}", to, user);
            return true;
        } catch (Exception e) {
            log.error("Error enviando correo: {}", e.getMessage());
            return false;
        }
    }

    // ── Resultado ───────────────────────────────────────────────

    /** Resultado de sincronizar una carpeta. */
    public record SyncResult(String carpeta, int nuevos, int total) {}
}
