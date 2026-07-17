package com.emailai.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

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
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import com.emailai.ai.AiService;
import com.emailai.domain.entities.Entrenamiento;
import com.emailai.domain.entities.Mensaje;

// Servicio central de correo: conexión IMAP/POP3, sincronización inteligente,
// clasificación Weka, envío SMTP y resumen con IA.
// Cada método abre y cierra su propia conexión para evitar sesiones stale.
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final MensajeService mensajeService;
    private final SpamIaService spamIaService;
    private final AiService aiService;
    private final RemitenteConfiableService remitenteService;
    private final EntrenamientoService entrenamientoService;

    public MailService(MensajeService mensajeService, SpamIaService spamIaService,
                       AiService aiService, RemitenteConfiableService remitenteService,
                       EntrenamientoService entrenamientoService) {
        this.mensajeService = mensajeService;
        this.spamIaService = spamIaService;
        this.aiService = aiService;
        this.remitenteService = remitenteService;
        this.entrenamientoService = entrenamientoService;
    }

    // ── Conexión IMAP/POP3 ──────────────────────────────────────
    // Soporta SSL según puerto y STARTTLS. POP3 configurado para no borrar.

    private Store conectarCorreo(String host, int port, String user, String password,
                                  String protocol) throws MessagingException {
        boolean ssl = port == 993 || port == 995;
        String propPrefix = "mail." + protocol + (ssl ? "s" : "");

        Properties props = new Properties();
        props.put(propPrefix + ".host", host);
        props.put(propPrefix + ".port", String.valueOf(port));
        if (ssl) {
            props.put(propPrefix + ".ssl.enable", "true");
        } else {
            props.put(propPrefix + ".starttls.enable", "true");
        }
        props.put(propPrefix + ".connectiontimeout", "10000");
        props.put(propPrefix + ".timeout", "10000");

        // POP3: NO eliminar mensajes del servidor al leerlos
        if ("pop3".equals(protocol)) {
            props.put("mail.pop3.rsetbeforequit", "true");
            props.put("mail.pop3.delete", "false");
        }

        Session session = Session.getInstance(props);
        String storeProtocol = protocol + (ssl ? "s" : "");
        Store store = session.getStore(storeProtocol);
        store.connect(host, port, user, password);
        return store;
    }

    /**
     * Conecta según el tipo de conexión (IMAP | POP3).
     * Para POP3 usa puerto 995 por defecto si no se especifica.
     */
    private Store conectarPorTipo(String host, int port, String user, String password,
                                   String tipoConexion) throws MessagingException {
        if ("POP3".equalsIgnoreCase(tipoConexion)) {
            return conectarCorreo(host, port > 0 ? port : 995, user, password, "pop3");
        }
        return conectarCorreo(host, port > 0 ? port : 993, user, password, "imap");
    }

    // ── Carpetas ────────────────────────────────────────────────
    // Filtra carpetas Gmail para no mostrar etiquetas internas.
    // POP3 solo devuelve INBOX.

    public List<String> listarCarpetas(String host, String user, String password)
            throws MessagingException {
        return listarCarpetas(host, user, password, "IMAP");
    }

    public List<String> listarCarpetas(String host, String user, String password,
                                        String tipoConexion) throws MessagingException {
        if ("POP3".equalsIgnoreCase(tipoConexion)) {
            return List.of("INBOX");
        }

        Store store = conectarPorTipo(host, 993, user, password, "IMAP");
        try {
            Folder defaultFolder = store.getDefaultFolder();
            return Arrays.stream(defaultFolder.list("*"))
                    .map(Folder::getFullName)
                    .filter(n -> !n.startsWith("[Gmail]") || n.equals("INBOX")
                            || n.equals("[Gmail]/Spam") || n.equals("[Gmail]/Trash")
                            || n.equals("[Gmail]/All Mail") || n.equals("[Gmail]/Starred")
                            || n.equals("[Gmail]/Important"))
                    .sorted(Comparator.comparing(n -> !n.equals("INBOX")))
                    .toList();
        } finally {
            store.close();
        }
    }

    // ── Sincronización inteligente ──────────────────────────────
    // Compara Message-IDs locales con servidor, solo descarga nuevos.
    // Primero cabeceras (ENVELOPE), luego cuerpo solo si es nuevo.

    public SyncResult sincronizarCarpeta(String host, String user, String password,
                                           String cuentaHash, String carpeta)
            throws MessagingException, IOException {
        return sincronizarCarpeta(host, user, password, cuentaHash, carpeta, 50, "IMAP");
    }

    public SyncResult sincronizarCarpeta(String host, String user, String password,
                                           String cuentaHash, String carpeta, int maxDescargar)
            throws MessagingException, IOException {
        return sincronizarCarpeta(host, user, password, cuentaHash, carpeta, maxDescargar, "IMAP");
    }

    public SyncResult sincronizarCarpeta(String host, String user, String password,
                                           String cuentaHash, String carpeta, int maxDescargar,
                                           String tipoConexion)
            throws MessagingException, IOException {
        int port = "POP3".equalsIgnoreCase(tipoConexion) ? 995 : 993;
        Store store = conectarPorTipo(host, port, user, password, tipoConexion);

        try {
            Folder folder;
            if ("POP3".equalsIgnoreCase(tipoConexion)) {
                folder = store.getDefaultFolder();
            } else {
                folder = store.getFolder(carpeta);
            }
            folder.open(Folder.READ_ONLY);

            int totalEnServidor = folder.getMessageCount();
            int noLeidosEnServidor = folder.getUnreadMessageCount();

            if (totalEnServidor == 0) {
                folder.close(false);
                return new SyncResult(carpeta, 0, 0, 0);
            }

            // Obtener los últimos maxDescargar mensajes del servidor
            int start = Math.max(1, totalEnServidor - maxDescargar + 1);
            Message[] serverMsgs = folder.getMessages(start, totalEnServidor);

            // Paso 1: fetch solo cabeceras (ENVELOPE) para obtener Message-IDs
            FetchProfile fpCabeceras = new FetchProfile();
            fpCabeceras.add(FetchProfile.Item.ENVELOPE);
            fpCabeceras.add(FetchProfile.Item.FLAGS);
            folder.fetch(serverMsgs, fpCabeceras);

            // Obtener Message-IDs que ya tenemos en local
            Set<String> localUids = mensajeService.listarUidsPorCarpeta(cuentaHash, carpeta);

            // Identificar cuáles son nuevos
            List<Message> mensajesNuevos = new ArrayList<>();
            for (Message msg : serverMsgs) {
                String msgId = obtenerMessageId(msg);
                if (msgId != null && !localUids.contains(msgId)) {
                    mensajesNuevos.add(msg);
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("📬 {}·{}: {}/{} en servidor, {} nuevos de los últimos {}",
                        cuentaHash, carpeta, totalEnServidor, noLeidosEnServidor,
                        mensajesNuevos.size(), serverMsgs.length);
            }

            // Paso 2: fetch contenido completo solo de los nuevos
            if (!mensajesNuevos.isEmpty()) {
                Message[] nuevosArray = mensajesNuevos.toArray(new Message[0]);
                FetchProfile fpCompleto = new FetchProfile();
                fpCompleto.add(FetchProfile.Item.ENVELOPE);
                fpCompleto.add(FetchProfile.Item.CONTENT_INFO);
                fpCompleto.add(FetchProfile.Item.FLAGS);
                folder.fetch(nuevosArray, fpCompleto);

                int descargados = 0;
                for (Message msg : nuevosArray) {
                    Mensaje m = convertirMensaje(msg, cuentaHash, carpeta);
                    if (m != null) {
                        clasificarMensaje(m);
                        mensajeService.guardarOActualizar(m);
                        descargados++;
                    }
                }

                folder.close(false);
                return new SyncResult(carpeta, totalEnServidor, noLeidosEnServidor, descargados);
            }

            folder.close(false);
            return new SyncResult(carpeta, totalEnServidor, noLeidosEnServidor, 0);
        } finally {
            store.close();
        }
    }

    // Si no tiene Message-ID (raro), genera uno único
    private String obtenerMessageId(Message msg) {
        try {
            String[] mid = msg.getHeader("Message-ID");
            if (mid != null && mid.length > 0 && mid[0] != null && !mid[0].isBlank()) {
                return mid[0].trim();
            }
        } catch (MessagingException e) {
            log.warn("No se pudo leer Message-ID: {}", e.getMessage());
        }
        // Fallback: generar un ID único
        try {
            String subj = msg.getSubject();
            return "gen-" + System.nanoTime() + "-" + (subj != null ? subj.hashCode() : 0);
        } catch (MessagingException e) {
            return "gen-" + System.nanoTime();
        }
    }

    private Mensaje convertirMensaje(Message msg, String cuentaHash, String carpetaImap) {
        try {
            Mensaje m = new Mensaje();
            m.setUid(msg.getHeader("Message-ID") != null ? msg.getHeader("Message-ID")[0]
                    : String.valueOf(System.nanoTime()));
            m.setCuentaHash(cuentaHash);
            m.setCarpetaImap(carpetaImap);

            if (msg.getFrom() != null && msg.getFrom().length > 0) {
                m.setRemitente(InternetAddress.toString(msg.getFrom()));
            }
            if (msg.getRecipients(jakarta.mail.Message.RecipientType.TO) != null) {
                m.setDestinatarios(InternetAddress.toString(
                        msg.getRecipients(jakarta.mail.Message.RecipientType.TO)));
            }
            if (msg.getRecipients(jakarta.mail.Message.RecipientType.CC) != null) {
                m.setCc(InternetAddress.toString(
                        msg.getRecipients(jakarta.mail.Message.RecipientType.CC)));
            }
            m.setAsunto(msg.getSubject());
            m.setFechaRecepcion(msg.getReceivedDate() != null
                    ? msg.getReceivedDate().toInstant().toString()
                    : (msg.getSentDate() != null ? msg.getSentDate().toInstant().toString() : ""));

            if (msg.isMimeType("text/plain")) {
                m.setCuerpo((String) msg.getContent());
            } else if (msg.isMimeType("text/html")) {
                m.setHtml((String) msg.getContent());
            } else if (msg.getContent() instanceof MimeMultipart multipart) {
                extraerPartes(multipart, m);
            }

            return m;
        } catch (Exception e) {
            log.warn("Error convirtiendo mensaje: {}", e.getMessage());
            return null;
        }
    }

    private void extraerPartes(MimeMultipart multipart, Mensaje m)
            throws MessagingException, IOException {
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

    // ── Clasificación con Weka ───────────────────────────────────
    // Lista blanca primero; si no está, aplica Naive Bayes.
    // forzarCategoria() guarda el ejemplo y reentrena.

    public Mensaje clasificarMensaje(Mensaje mensaje) {
        if (mensaje.getCategoria() != null && !"DESCONOCIDO".equals(mensaje.getCategoria())) {
            return mensaje;
        }
        try {
            if (mensaje.getRemitente() != null
                    && remitenteService.esConfiable(mensaje.getRemitente())) {
                mensaje.setCategoria("LEGITIMO");
                return mensaje;
            }
            SpamIaService.ClaseCorreo clase =
                    spamIaService.clasificar(mensaje.getCuentaHash(), mensaje);
            mensaje.setCategoria(clase.name());
        } catch (Exception e) {
            log.warn("Error clasificando mensaje: {}", e.getMessage());
            mensaje.setCategoria("DESCONOCIDO");
        }
        return mensaje;
    }

    public Mensaje forzarCategoria(Mensaje mensaje, String categoria) {
        mensaje.setCategoria(categoria.toUpperCase());
        Entrenamiento ej = new Entrenamiento();
        ej.setCuentaHash(mensaje.getCuentaHash());
        ej.setTipo("spam");
        ej.setRemitente(mensaje.getRemitente());
        ej.setAsunto(mensaje.getAsunto());
        ej.setCuerpo(mensaje.getCuerpo() != null ? mensaje.getCuerpo() : mensaje.getHtml());
        ej.setEtiqueta(categoria.toUpperCase());
        ej.setCreatedAt(java.time.LocalDateTime.now());
        entrenamientoService.guardar(ej);
        reentrenarModeloConEntrenamiento(mensaje.getCuentaHash());
        return mensaje;
    }

    public void reentrenarModeloConEntrenamiento(String cuentaHash) {
        try {
            var ejemplos = entrenamientoService.listarPorCuenta(cuentaHash);
            if (ejemplos.isEmpty()) return;
            var mensajes = ejemplos.stream().map(e -> {
                Mensaje m = new Mensaje();
                m.setCuentaHash(e.getCuentaHash());
                m.setRemitente(e.getRemitente());
                m.setAsunto(e.getAsunto());
                m.setCuerpo(e.getCuerpo());
                m.setCategoria(e.getEtiqueta());
                return m;
            }).toList();
            if (mensajes.size() >= 3) {
                spamIaService.entrenarModelo(cuentaHash, mensajes);
                log.info("Modelo reentrenado para cuenta {} con {} ejemplos",
                        cuentaHash, mensajes.size());
            }
        } catch (Exception e) {
            log.error("Error reentrenando modelo: {}", e.getMessage());
        }
    }

    public void reentrenarModelo(String cuentaHash) {
        try {
            var mensajes = mensajeService.listarPorCarpeta(cuentaHash, "INBOX");
            var entrenamiento = mensajes.stream()
                    .filter(m -> m.getCategoria() != null
                            && !"DESCONOCIDO".equals(m.getCategoria()))
                    .toList();
            if (entrenamiento.size() >= 5) {
                spamIaService.entrenarModelo(cuentaHash, entrenamiento);
                log.info("Modelo reentrenado para cuenta {} con {} ejemplos",
                        cuentaHash, entrenamiento.size());
            }
        } catch (Exception e) {
            log.error("Error reentrenando modelo: {}", e.getMessage());
        }
    }

    // ── Resumen IA (LM Studio) ───────────────────────────────────

    public String generarResumen(Mensaje mensaje) {
        String contenido = mensaje.getCuerpo() != null ? mensaje.getCuerpo() : mensaje.getHtml();
        return aiService.generarResumen(contenido != null ? contenido : "");
    }

    public String sugerirRespuesta(Mensaje mensaje) {
        String contenido = mensaje.getCuerpo() != null ? mensaje.getCuerpo() : mensaje.getHtml();
        return aiService.sugerirRespuesta(contenido != null ? contenido : "");
    }

    // ── Sincronización completa ─────────────────────────────────
    // Itera todas las carpetas y sincroniza cada una.

    public List<SyncResult> sincronizarTodo(String host, String user, String password,
                                             String cuentaHash)
            throws MessagingException, IOException {
        return sincronizarTodo(host, user, password, cuentaHash, 50, "IMAP");
    }

    public List<SyncResult> sincronizarTodo(String host, String user, String password,
                                             String cuentaHash, int maxDescargar)
            throws MessagingException, IOException {
        return sincronizarTodo(host, user, password, cuentaHash, maxDescargar, "IMAP");
    }

    public List<SyncResult> sincronizarTodo(String host, String user, String password,
                                             String cuentaHash, int maxDescargar,
                                             String tipoConexion)
            throws MessagingException, IOException {
        List<String> carpetas = listarCarpetas(host, user, password, tipoConexion);
        List<SyncResult> resultados = new ArrayList<>();
        for (String carpeta : carpetas) {
            resultados.add(sincronizarCarpeta(host, user, password, cuentaHash,
                    carpeta, maxDescargar, tipoConexion));
        }
        // Solo reentrenar si hay clasificaciones nuevas (forzarCategoria), no tras cada sync
        for (String carpeta : carpetas) {
            mensajeService.limpiarAntiguos(cuentaHash, carpeta);
        }
        return resultados;
    }

    // ── Acciones en servidor ────────────────────────────────────
    // POP3 no soporta borrado ni mover entre carpetas.

    public boolean eliminarDelServidor(String host, String user, String password,
                                        String carpetaOrigen, String uid) {
        return eliminarDelServidor(host, user, password, carpetaOrigen, uid, "IMAP");
    }

    public boolean eliminarDelServidor(String host, String user, String password,
                                        String carpetaOrigen, String uid,
                                        String tipoConexion) {
        if ("POP3".equalsIgnoreCase(tipoConexion)) {
            log.info("POP3 no soporta borrado en servidor");
            return false;
        }
        try {
            Store store = conectarPorTipo(host, 993, user, password, "IMAP");
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
            log.warn("Error eliminando del servidor: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Mueve un mensaje a otra carpeta.
     * POP3 no lo soporta (solo INBOX, sin carpetas).
     */
    public boolean moverACarpeta(String host, String user, String password,
                                  String carpetaOrigen, String carpetaDestino, String uid) {
        return moverACarpeta(host, user, password, carpetaOrigen, carpetaDestino, uid, "IMAP");
    }

    public boolean moverACarpeta(String host, String user, String password,
                                  String carpetaOrigen, String carpetaDestino, String uid,
                                  String tipoConexion) {
        if ("POP3".equalsIgnoreCase(tipoConexion)) {
            log.info("POP3 no soporta mover entre carpetas");
            return false;
        }
        try {
            Store store = conectarPorTipo(host, 993, user, password, "IMAP");
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

    // ── Envío SMTP ──────────────────────────────────────────────
    // Autenticación con STARTTLS, timeout 10s.

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
            msg.setRecipients(jakarta.mail.Message.RecipientType.TO,
                    InternetAddress.parse(to));
            if (cc != null && !cc.isBlank()) {
                msg.setRecipients(jakarta.mail.Message.RecipientType.CC,
                        InternetAddress.parse(cc));
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

    // Resultado de sincronización de una carpeta
    public record SyncResult(String carpeta, int totalServer, int noLeidos, int descargados) {}
}
