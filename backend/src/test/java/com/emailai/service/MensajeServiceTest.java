package com.emailai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.domain.entities.Mensaje;

/**
 * Test del servicio de mensajes: upsert por UID, listado y búsqueda.
 */
@SpringBootTest
@Transactional
class MensajeServiceTest {

    @Autowired
    private MensajeService mensajeService;

    @Test
    void upsertYListado() {
        String cuenta = "cuenta1";
        String carpeta = "INBOX";

        // Guardar nuevo
        Mensaje m1 = new Mensaje();
        m1.setUid("uid-100");
        m1.setCuentaHash(cuenta);
        m1.setCarpetaImap(carpeta);
        m1.setRemitente("remitente@example.com");
        m1.setAsunto("Hola");
        m1.setCuerpo("Cuerpo del mensaje");
        m1.setCategoria("DESCONOCIDO");
        m1.setPrioridad("NORMAL");
        m1.setFechaRecepcion("2026-07-14T10:00:00");
        mensajeService.guardarOActualizar(m1);

        // Upsert: actualizar el mismo UID
        m1.setAsunto("Hola (actualizado)");
        mensajeService.guardarOActualizar(m1);

        List<Mensaje> lista = mensajeService.listarPorCarpeta(cuenta, carpeta);
        assertEquals(1, lista.size(), "El upsert no debe duplicar");
        assertEquals("Hola (actualizado)", lista.get(0).getAsunto());
        assertEquals(1, mensajeService.contar(cuenta, carpeta));
    }

    @Test
    void buscarEnBandeja() {
        Mensaje m = new Mensaje();
        m.setUid("uid-200");
        m.setCuentaHash("cuenta2");
        m.setCarpetaImap("INBOX");
        m.setRemitente("spam@test.com");
        m.setAsunto("Oferta especial");
        m.setCuerpo("Compra ahora");
        m.setCategoria("SPAM");
        m.setPrioridad("NORMAL");
        m.setFechaRecepcion("2026-07-14T11:00:00");
        mensajeService.guardarOActualizar(m);

        List<Mensaje> resultados = mensajeService.buscar("cuenta2", "INBOX", "oferta");
        assertFalse(resultados.isEmpty());
        assertTrue(resultados.stream().anyMatch(msg -> msg.getAsunto().contains("Oferta")));
    }
}
