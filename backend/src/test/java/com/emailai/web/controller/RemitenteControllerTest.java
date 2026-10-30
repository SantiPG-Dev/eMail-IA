package com.emailai.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class RemitenteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void alta_listado_y_borrado() throws Exception {
        mockMvc.perform(post("/api/remitentes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remitente\":\"jefe@empresa.com\",\"categoria\":\"LEGITIMO\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/remitentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].remitente").value("jefe@empresa.com"))
                .andExpect(jsonPath("$[0].categoria").value("LEGITIMO"));

        mockMvc.perform(delete("/api/remitentes").param("remitente", "JEFE@empresa.com"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/remitentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void categoriaInvalida_400() throws Exception {
        mockMvc.perform(post("/api/remitentes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remitente\":\"x@y.com\",\"categoria\":\"OTRA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emailInvalido_400() throws Exception {
        mockMvc.perform(post("/api/remitentes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remitente\":\"no-es-email\",\"categoria\":\"SPAM\"}"))
                .andExpect(status().isBadRequest());
    }
}
