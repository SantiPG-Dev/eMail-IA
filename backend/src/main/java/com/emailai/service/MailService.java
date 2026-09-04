package com.emailai.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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

import com.emailai.ai.AiService;
import com.emailai.domain.entities.Mensaje;

// Servicio central de correo: conexión IMAP/POP3, sincronización inteligente,
// acciones en servidor y envío SMTP.
// La conversión MIME vive en MimeParser y la clasificación Weka/entrenamiento
// en ClasificacionCorreoService (aquí solo delega la API pública).
// Cada método abre y cierra su propia conexión para evitar sesiones stale.
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final MensajeService mensajeService;
    private final ClasificacionCorreoService clasificacionService;
    private final AiService aiService;
    private final int mailTimeoutMs;

    public MailService(MensajeService mensajeService,
                       ClasificacionCorreoService clasificacionService,
                       AiService aiService,
                       @org.springframework.beans.factory.annotation.Value("${emailai.mail.timeout:10000}") int mailTimeoutMs) {
        this.mensajeService = mensajeService;
        this.clasificacionService = clasificacionService;
        this.aiService = aiService;
        this.mailTimeoutMs = mailTimeoutMs;
    }

    // ── Conexión IMAP/POP3 ──────────────────────────────────────
    // Soporta SSL según puerto y STARTTLS. POP3 configurado para no borrar.
    // Con esOAuth=true autentica con XOAUTH2 (token SASL) en vez de password.

    private Store conectarCorreo(String host, int port, String user, String password,
                                  String protocol) throws MessagingException {
        return conectarCorreo(host, port, user, password, protocol, false);
    }

    private Store conectarCorreo(String host, int port, String user, String token,
                                  String protocol, boolean esOAuth) throws MessagingException {
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
        props.put(propPrefix + ".connectiontimeout", String.valueOf(mailTimeoutMs));
        props.put(propPrefix + ".timeout", String.valueOf(mailTimeoutMs));

        // POP3: NO eliminar mensajes del servidor al leerlos
        if ("pop3".equals(protocol)) {
            props.put("mail.pop3.rsetbeforequit", "true");
            props.put("mail.pop3.delete", "false");
        }

        if (esOAuth) {
            // Gmail/Microsoft rechazan el token como password LOGIN: exigen SASL
            // XOAUTH2. Con auth.mechanisms=XOAUTH2, Jakarta Mail trata el secret
            // como token SASL al conectar.
            props.put(propPrefix + ".auth.mechanisms", "XOAUTH2");
        }

        Session session = Session.getInstance(props);
        String storeProtocol = protocol + (ssl ? "s" : "");
        Store store = session.getStore(storeProtocol);
        store.connect(host, port, user, token);
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

    /**
     * Conexión IMAP con autenticación OAuth2 (XOAUTH2 SASL).
     * Para cuentas Google/Microsoft donde no hay password de aplicación.
     */
    public Store conectarOAuth(String host, int port, String user, String token)
            throws MessagingException {
        return conectarCorreo(host, port > 0 ? port : 993, user, token, "imap", true);
    }

    /**
     * Conexión unificada: OAuth usa XOAUTH2 (solo IMAP); password según tipo.
     */
    private Store conectarCuenta(String host, int port, String user, String secret,
                                 String tipoConexion, boolean esOAuth) throws MessagingException {
        if (esOAuth) {
            return conectarCorreo(host, port, user, secret, "imap", true);
        }
        return conectarPorTipo(host, port, user, secret, tipoConexion);
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
        return listarCarpetas(host, user, password, tipoConexion, false);
    }

    public List<String> listarCarpetas(String host, String user, String secret,
                                        String tipoConexion, boolean esOAuth) throws MessagingException {
        if ("POP3".equalsIgnoreCase(tipoConexion)) {
            return List.of("INBOX");
        }

        Store store = conectarCuenta(host, 993, user, secret, tipoConexion, esOAuth);
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
        return sincronizarCarpeta(host, user, password, cuentaHash, carpeta, maxDescargar, tipoConexion, false);
    }

    public SyncResult sincronizarCarpeta(String host, String user, String secret,
                                           String cuentaHash, String carpeta, int maxDescargar,
                                           String tipoConexion, boolean esOAuth)
            throws MessagingException, IOException {
        int port = "POP3".equalsIgnoreCase(tipoConexion) ? 995 : 993;
        Store store = conectarCuenta(host, port, user, secret, tipoConexion, esOAuth);

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
                String msgId = MimeParser.obtenerMessageId(msg);
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
                    Mensaje m = MimeParser.convertir(msg, cuentaHash, carpeta);
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

    // ── Clasificación con Weka ───────────────────────────────────
    // Delega en ClasificacionCorreoService; la API pública de MailService
    // se mantiene para no tocar los controllers.

    public Mensaje clasificarMensaje(Mensaje mensaje) {
        return clasificacionService.clasificarMensaje(mensaje);
    }

    public ClasificacionCorreoService.ResultadoForzado forzarCategoria(Mensaje mensaje, String categoria) {
        return clasificacionService.forzarCategoria(mensaje, categoria);
    }

    public void reentrenarModeloConEntrenamiento(String cuentaHash) {
        clasificacionService.reentrenarModeloConEntrenamiento(cuentaHash);
    }

    public void reentrenarModelo(String cuentaHash) {
        clasificacionService.reentrenarModelo(cuentaHash);
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
        return sincronizarTodo(host, user, password, cuentaHash, maxDescargar, tipoConexion, false);
    }

    public List<SyncResult> sincronizarTodo(String host, String user, String secret,
                                             String cuentaHash, int maxDescargar,
                                             String tipoConexion, boolean esOAuth)
            throws MessagingException, IOException {
        List<String> carpetas = listarCarpetas(host, user, secret, tipoConexion, esOAuth);
        List<SyncResult> resultados = new ArrayList<>();
        for (String carpeta : carpetas) {
            resultados.add(sincronizarCarpeta(host, user, secret, cuentaHash,
                    carpeta, maxDescargar, tipoConexion, esOAuth));
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
        return eliminarDelServidor(host, user, password, carpetaOrigen, uid, tipoConexion, false);
    }

    public boolean eliminarDelServidor(String host, String user, String secret,
                                        String carpetaOrigen, String uid,
                                        String tipoConexion, boolean esOAuth) {
        if ("POP3".equalsIgnoreCase(tipoConexion)) {
            log.info("POP3 no soporta borrado en servidor");
            return false;
        }
        try {
            Store store = conectarCuenta(host, 993, user, secret, tipoConexion, esOAuth);
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
        return moverACarpeta(host, user, password, carpetaOrigen, carpetaDestino, uid, tipoConexion, false);
    }

    public boolean moverACarpeta(String host, String user, String secret,
                                  String carpetaOrigen, String carpetaDestino, String uid,
                                  String tipoConexion, boolean esOAuth) {
        if ("POP3".equalsIgnoreCase(tipoConexion)) {
            log.info("POP3 no soporta mover entre carpetas");
            return false;
        }
        try {
            Store store = conectarCuenta(host, 993, user, secret, tipoConexion, esOAuth);
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
    // Autenticación con STARTTLS, timeout configurable (emailai.mail.timeout).
    // Con esOAuth=true usa XOAUTH2 en vez de LOGIN/PLAIN con password.

    public boolean enviarCorreo(String smtpHost, int smtpPort, String user, String password,
                                 String to, String cc, String subject, String body) {
        return enviarCorreo(smtpHost, smtpPort, user, password, to, cc, subject, body, false);
    }

    public boolean enviarCorreo(String smtpHost, int smtpPort, String user, String secret,
                                 String to, String cc, String subject, String body,
                                 boolean esOAuth) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", smtpPort);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.connectiontimeout", String.valueOf(mailTimeoutMs));
            props.put("mail.smtp.timeout", String.valueOf(mailTimeoutMs));
            if (esOAuth) {
                props.put("mail.smtp.sasl.enable", "true");
                props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
            }

            Session session = Session.getInstance(props, new jakarta.mail.Authenticator() {
                @Override
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new jakarta.mail.PasswordAuthentication(user, secret);
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

    // ── Verificación de credenciales ───────────────────────────
    // Conecta al servidor y cierra. Si no lanza excepción, las credenciales son válidas.
    // Usado por AuthController para login.

    public void probarConexion(String host, int puerto, String user, String password,
                                 String tipoConexion) throws MessagingException {
        Store store = conectarPorTipo(host, puerto, user, password, tipoConexion);
        store.close();
    }

    /**
     * Verifica credenciales OAuth2 contra el servidor IMAP (XOAUTH2).
     */
    public void probarConexionOAuth(String host, int puerto, String user, String token)
            throws MessagingException {
        Store store = conectarOAuth(host, puerto, user, token);
        store.close();
    }

    // Resultado de sincronización de una carpeta
    public record SyncResult(String carpeta, int totalServer, int noLeidos, int descargados) {}
}
