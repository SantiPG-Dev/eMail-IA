package com.emailai.service;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;

import com.emailai.domain.entities.Adjunto;
import com.emailai.domain.entities.Mensaje;

// Conversión de jakarta.mail.Message → entidad Mensaje: cabeceras, cuerpo
// text/plain|html, multiparte anidado y adjuntos con límite de tamaño.
// Sin estado: clase utilitaria estática, testeable sin servidor de correo.
public final class MimeParser {

    private static final Logger log = LoggerFactory.getLogger(MimeParser.class);

    /** Máximo tamaño de adjunto: 25 MB (evita BLOBs gigantes en H2). */
    private static final int MAX_ADJUNTO_BYTES = 25 * 1024 * 1024;

    private MimeParser() {}

    /** Convierte un Message del servidor a Mensaje; null si no se puede leer. */
    public static Mensaje convertir(Message msg, String cuentaHash, String carpetaImap) {
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

    /** Message-ID de cabecera, o uno generado si falta (raro). */
    public static String obtenerMessageId(Message msg) {
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

    private static void extraerPartes(MimeMultipart multipart, Mensaje m)
            throws MessagingException, IOException {
        for (int i = 0; i < multipart.getCount(); i++) {
            var part = multipart.getBodyPart(i);
            if (part.isMimeType("text/plain") && m.getCuerpo() == null) {
                m.setCuerpo((String) part.getContent());
            } else if (part.isMimeType("text/html") && m.getHtml() == null) {
                m.setHtml((String) part.getContent());
            } else if (part.getContent() instanceof MimeMultipart nested) {
                extraerPartes(nested, m);
            } else {
                extraerAdjunto(part, m);
            }
        }
    }

    /** Guarda como adjunto toda parte MIME que no sea texto del cuerpo. */
    private static void extraerAdjunto(Part part, Mensaje m) {
        try {
            String nombre = part.getFileName();
            if (nombre == null || nombre.isBlank()) return;

            // Límite de 25 MB por adjunto: no petar la H2 con blobs enormes.
            int tam = part.getSize();
            if (tam > MAX_ADJUNTO_BYTES) {
                log.warn("Adjunto '{}' de {} bytes descartado (máx {} MB)",
                        nombre, tam, MAX_ADJUNTO_BYTES / (1024 * 1024));
                return;
            }

            byte[] datos = part.getInputStream().readAllBytes();
            if (datos.length == 0) return;

            Adjunto adj = new Adjunto();
            adj.setNombre(nombre);
            adj.setMimeType(part.getContentType());
            adj.setTamanoBytes((long) datos.length);
            adj.setDatos(datos);
            m.addAdjunto(adj);
        } catch (Exception e) {
            log.warn("Error extrayendo adjunto de mensaje: {}", e.getMessage());
        }
    }
}
