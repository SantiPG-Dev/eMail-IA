package com.emailai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.emailai.domain.entities.Mensaje;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

/**
 * Tests de MimeParser con mensajes MimeMessage construidos en memoria
 * (sin servidor de correo).
 */
class MimeParserTest {

    private static final String CUENTA = "hash-test";

    private MimeMessage mensajeSimple() throws Exception {
        Session s = Session.getInstance(new Properties());
        MimeMessage msg = new MimeMessage(s);
        msg.setFrom(new InternetAddress("remitente@ejemplo.com"));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress("yo@test.com"));
        msg.setSubject("Asunto de prueba");
        msg.setText("Hola, este es el cuerpo en texto plano.");
        msg.saveChanges();
        return msg;
    }

    @Test
    void convertir_mensajePlano_mapeaCamposBasicos() throws Exception {
        Mensaje m = MimeParser.convertir(mensajeSimple(), CUENTA, "INBOX");

        assertNotNull(m);
        assertEquals(CUENTA, m.getCuentaHash());
        assertEquals("INBOX", m.getCarpetaImap());
        assertEquals("Asunto de prueba", m.getAsunto());
        assertEquals("Hola, este es el cuerpo en texto plano.", m.getCuerpo());
        assertTrue(m.getRemitente().contains("remitente@ejemplo.com"));
        assertNotNull(m.getUid());
        assertTrue(m.getAdjuntos() == null || m.getAdjuntos().isEmpty());
    }

    @Test
    void convertir_multiparteMultipart_extraeTextoYAdjunto() throws Exception {
        Session s = Session.getInstance(new Properties());
        MimeMessage msg = new MimeMessage(s);
        msg.setFrom(new InternetAddress("con@adjunto.com"));
        msg.setSubject("Con adjunto");

        MimeBodyPart texto = new MimeBodyPart();
        texto.setText("cuerpo del correo");
        MimeBodyPart adjunto = new MimeBodyPart();
        adjunto.setFileName("informe.dat");
        // DataHandler con DataSource, como los mensajes reales del stream IMAP
        // (setContent directo necesita DCH que no está en el classpath de test)
        adjunto.setDataHandler(new jakarta.activation.DataHandler(
                new ByteArrayDataSource(new byte[]{1, 2, 3, 4}, "application/octet-stream")));
        Multipart mp = new MimeMultipart();
        mp.addBodyPart(texto);
        mp.addBodyPart(adjunto);
        msg.setContent(mp);
        msg.saveChanges();

        Mensaje m = MimeParser.convertir(msg, CUENTA, "INBOX");

        assertEquals("cuerpo del correo", m.getCuerpo());
        assertEquals(1, m.getAdjuntos().size());
        assertEquals("informe.dat", m.getAdjuntos().get(0).getNombre());
        assertTrue(m.getAdjuntos().get(0).getMimeType().startsWith("application/octet-stream"));
    }

    @Test
    void obtenerMessageId_devuelveCabeceraOTimpoGenerado() throws Exception {
        MimeMessage msg = mensajeSimple();
        // saveChanges genera Message-ID automáticamente: lo quitamos para
        // comprobar el fallback con prefijo gen-
        msg.removeHeader("Message-ID");
        assertTrue(MimeParser.obtenerMessageId(msg).startsWith("gen-"));

        msg.setHeader("Message-ID", "<abc-123@servidor>");
        assertEquals("<abc-123@servidor>", MimeParser.obtenerMessageId(msg));
    }

    @Test
    void convertir_parteSinNombre_noCreaAdjunto() throws Exception {
        Session s = Session.getInstance(new Properties());
        MimeMessage msg = new MimeMessage(s);
        msg.setFrom(new InternetAddress("x@y.com"));
        msg.setSubject("Parte binaria sin filename");

        MimeBodyPart texto = new MimeBodyPart();
        texto.setText("cuerpo");
        MimeBodyPart sinNombre = new MimeBodyPart();
        sinNombre.setContent(new byte[]{1, 2, 3}, "application/octet-stream");
        Multipart mp = new MimeMultipart();
        mp.addBodyPart(texto);
        mp.addBodyPart(sinNombre);
        msg.setContent(mp);
        msg.saveChanges();

        Mensaje m = MimeParser.convertir(msg, CUENTA, "INBOX");

        assertEquals("cuerpo", m.getCuerpo());
        assertTrue(m.getAdjuntos() == null || m.getAdjuntos().isEmpty(),
                "Parte sin filename no debe guardarse como adjunto");
    }
}
