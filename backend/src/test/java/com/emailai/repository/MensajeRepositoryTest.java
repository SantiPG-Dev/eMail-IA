package com.emailai.repository;

import com.emailai.domain.entities.Adjunto;
import com.emailai.domain.entities.Mensaje;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests del data layer de mensajes contra H2 cifrada (patrón @SpringBootTest del proyecto).
 * Cubre las queries derivadas usadas por la paginación y el listado por carpeta IMAP.
 */
@SpringBootTest
@Transactional
class MensajeRepositoryTest {

    @Autowired
    private MensajeRepository repo;

    private Mensaje nuevo(String uid, String hash, String carpeta, String fecha) {
        Mensaje m = new Mensaje();
        m.setUid(uid);
        m.setCuentaHash(hash);
        m.setCarpetaImap(carpeta);
        m.setFechaRecepcion(fecha);
        m.setCuerpo("cuerpo de prueba");
        return m;
    }

    @Test
    void findByCuentaYcarpeta_devuelveOrdenadoPorFechaDesc() {
        repo.save(nuevo("u1", "hashA", "INBOX", "2026-07-01"));
        repo.save(nuevo("u2", "hashA", "INBOX", "2026-07-03"));
        repo.save(nuevo("u3", "hashA", "INBOX", "2026-07-02"));
        repo.save(nuevo("u4", "hashB", "INBOX", "2026-07-09")); // otra cuenta

        List<Mensaje> res = repo.findByCuentaHashAndCarpetaImapOrderByFechaRecepcionDescIdDesc("hashA", "INBOX");

        assertEquals(3, res.size());
        assertEquals("2026-07-03", res.get(0).getFechaRecepcion());
        assertEquals("2026-07-01", res.get(2).getFechaRecepcion());
    }

    @Test
    void countByCuentaYcarpeta_cuentaPorCarpeta() {
        repo.save(nuevo("u10", "hashC", "INBOX", "2026-07-01"));
        repo.save(nuevo("u11", "hashC", "INBOX", "2026-07-02"));
        repo.save(nuevo("u12", "hashC", "Sent", "2026-07-02"));

        assertEquals(2, repo.countByCuentaHashAndCarpetaImap("hashC", "INBOX"));
        assertEquals(1, repo.countByCuentaHashAndCarpetaImap("hashC", "Sent"));
        assertEquals(0, repo.countByCuentaHashAndCarpetaImap("hashC", "Drafts"));
    }

    @Test
    void paginacion_limitaResultados() {
        for (int i = 0; i < 5; i++) {
            repo.save(nuevo("p" + i, "hashP", "INBOX", "2026-07-0" + (i + 1)));
        }

        List<Mensaje> pagina1 = repo.findByCuentaHashAndCarpetaImapOrderByFechaRecepcionDescIdDesc(
                "hashP", "INBOX", PageRequest.of(0, 2));
        List<Mensaje> pagina2 = repo.findByCuentaHashAndCarpetaImapOrderByFechaRecepcionDescIdDesc(
                "hashP", "INBOX", PageRequest.of(1, 2));

        assertEquals(2, pagina1.size());
        assertEquals(2, pagina2.size());
        // Orden descendente: la primera página contiene las fechas más recientes
        assertTrue(pagina1.get(0).getFechaRecepcion().compareTo(pagina1.get(1).getFechaRecepcion()) >= 0);
    }

    @Test
    void adjuntos_persistenConCascadeYSeCarganConElMensaje() {
        Mensaje m = nuevo("u-adj", "hashAdj", "INBOX", "2026-07-05");
        Adjunto adj = new Adjunto();
        adj.setNombre("factura.pdf");
        adj.setMimeType("application/pdf");
        adj.setTamanoBytes(1234L);
        adj.setDatos(new byte[]{1, 2, 3, 4});
        m.addAdjunto(adj);
        repo.save(m);

        List<Mensaje> res = repo.findByCuentaHashAndCarpetaImapOrderByFechaRecepcionDescIdDesc("hashAdj", "INBOX");
        assertEquals(1, res.size());
        Mensaje cargado = res.get(0);
        assertNotNull(cargado.getAdjuntos());
        assertEquals(1, cargado.getAdjuntos().size());
        assertEquals("factura.pdf", cargado.getAdjuntos().get(0).getNombre());
        assertEquals(1234L, cargado.getAdjuntos().get(0).getTamanoBytes());
    }
}
