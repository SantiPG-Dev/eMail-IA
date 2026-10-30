package com.emailai.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.domain.entities.Mensaje;
import com.emailai.service.MensajeService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class MensajeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MensajeService mensajeService;

    @Test
    void listar_cuentaVacia_200() throws Exception {
        mockMvc.perform(get("/api/mensajes").param("cuentaHash", "cuenta-inexistente-test"))
                .andExpect(status().isOk());
    }

    @Test
    void marcarLeido_200_yDevuelveLeidoTrue() throws Exception {
        Mensaje m = new Mensaje();
        m.setUid("uid-leido-test");
        m.setCuentaHash("cuenta-test");
        m.setCarpetaImap("INBOX");
        m.setRemitente("a@b.c");
        m.setAsunto("prueba");
        m.setCategoria("LEGITIMO");
        m.setPrioridad("NORMAL");
        m.setFechaRecepcion("2026-10-05T00:00:00Z");
        m = mensajeService.guardarOActualizar(m);

        mockMvc.perform(post("/api/mensajes/" + m.getId() + "/leer"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"leido\": true}"));

        // y el upsert del sync no lo devuelve a no leído
        Mensaje reSync = new Mensaje();
        reSync.setUid(m.getUid());
        reSync.setCuentaHash(m.getCuentaHash());
        reSync.setCarpetaImap(m.getCarpetaImap());
        reSync.setRemitente("a@b.c");
        reSync.setAsunto("prueba");
        reSync.setCategoria("LEGITIMO");
        reSync.setPrioridad("NORMAL");
        reSync.setFechaRecepcion("2026-10-05T00:00:00Z");
        assert mensajeService.guardarOActualizar(reSync).getLeido();
    }

    @Test
    void obtenerInexistente_404() throws Exception {
        mockMvc.perform(get("/api/mensajes/999999"))
                .andExpect(status().isNotFound());
    }
}
