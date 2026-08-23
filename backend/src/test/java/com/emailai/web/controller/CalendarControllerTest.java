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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listar_vacio_200() throws Exception {
        mockMvc.perform(get("/api/calendario"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));
    }

    @Test
    void listarPorFecha_200() throws Exception {
        mockMvc.perform(get("/api/calendario/fecha/2026-07-29"))
                .andExpect(status().isOk());
    }

    @Test
    void obtenerInexistente_404() throws Exception {
        mockMvc.perform(get("/api/calendario/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void crearActualizarEliminarEvento() throws Exception {
        String body = """
            {"fecha":"2026-09-01","hora":"10:30","titulo":"Cita medico",
             "detalle":"Revisión anual","mensajeId":42}
            """;
        var creado = mockMvc.perform(post("/api/calendario")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Cita medico"))
                .andExpect(jsonPath("$.hora").value("10:30"))
                .andExpect(jsonPath("$.todoElDia").value(false))
                .andExpect(jsonPath("$.mensajeId").value(42))
                .andReturn().getResponse().getContentAsString();
        int id = Integer.parseInt(creado.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // Actualizar: pasa a todo el día con fin
        String update = """
            {"fecha":"2026-09-01","todoElDia":true,"fechaFin":"2026-09-02",
             "titulo":"Cita medico (jornada)","detalle":"Revisión anual"}
            """;
        mockMvc.perform(put("/api/calendario/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todoElDia").value(true))
                .andExpect(jsonPath("$.fechaFin").value("2026-09-02"))
                .andExpect(jsonPath("$.hora").doesNotExist())
                .andExpect(jsonPath("$.mensajeId").doesNotExist());

        mockMvc.perform(delete("/api/calendario/" + id))
                .andExpect(status().isNoContent());
    }
}
