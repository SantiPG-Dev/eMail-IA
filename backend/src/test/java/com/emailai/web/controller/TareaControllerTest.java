package com.emailai.web.controller;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class TareaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listar_vacio_200() throws Exception {
        mockMvc.perform(get("/api/tareas"))
                .andExpect(status().isOk());
    }

    @Test
    void obtenerInexistente_404() throws Exception {
        mockMvc.perform(get("/api/tareas/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void crearTareaApareceEnCalendario() throws Exception {
        String fecha = LocalDate.now().plusDays(3).toString();
        mockMvc.perform(post("/api/tareas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Pagar la luz\",\"fecha\":\"" + fecha + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("TAREA"));

        mockMvc.perform(get("/api/calendario/fecha/" + fecha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].titulo", hasItem("Pagar la luz")));
    }

    @Test
    void actualizarTareaConservaElTipo() throws Exception {
        String creado = mockMvc.perform(post("/api/tareas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Llamar\",\"estado\":\"pendiente\"}"))
                .andReturn().getResponse().getContentAsString();
        int id = Integer.parseInt(creado.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/tareas/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Llamar al banco\",\"estado\":\"completada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("TAREA"))
                .andExpect(jsonPath("$.estado").value("completada"));
    }
}
