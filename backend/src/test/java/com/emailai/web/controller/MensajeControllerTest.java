package com.emailai.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class MensajeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listar_cuentaVacia_200() throws Exception {
        mockMvc.perform(get("/api/mensajes").param("cuentaHash", "cuenta-inexistente-test"))
                .andExpect(status().isOk());
    }

    @Test
    void obtenerInexistente_404() throws Exception {
        mockMvc.perform(get("/api/mensajes/999999"))
                .andExpect(status().isNotFound());
    }
}
