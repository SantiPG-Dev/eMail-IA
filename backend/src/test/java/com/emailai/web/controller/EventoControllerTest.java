package com.emailai.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.emailai.security.JwtService;
import com.emailai.service.EventoSyncService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests del endpoint SSE /api/eventos:
 * - sin JWT → 401 (cae bajo la protección de /api/**)
 * - con JWT → stream async con evento inicial de conexión
 * - publicarSyncTerminado llega al stream como evento sync-terminado
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EventoSyncService eventoSyncService;

    @Test
    void eventos_sinToken_401() throws Exception {
        mockMvc.perform(get("/api/eventos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eventos_conToken_streamConEventos() throws Exception {
        String token = jwtService.generateToken("user@test.com");
        MvcResult mvc = mockMvc.perform(get("/api/eventos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Evento publicado mientras el stream está abierto
        eventoSyncService.publicarSyncTerminado("a@b.c", 2, 10, 3);
        // Cerrar emitters para poder despachar la respuesta async en MockMvc
        for (SseEmitter e : eventoSyncService.emitters()) {
            e.complete();
        }

        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:conexion")))
                .andExpect(content().string(containsString("event:sync-terminado")))
                .andExpect(content().string(containsString("\"descargados\":2")))
                .andExpect(content().string(containsString("\"cuenta\":\"a@b.c\"")));

        // complete() sobre emitters inicializados dispara onCompletion → desregistro
        assertEquals(0, eventoSyncService.emitters().size(),
                "el emitter completado debe desregistrarse");
    }
}
