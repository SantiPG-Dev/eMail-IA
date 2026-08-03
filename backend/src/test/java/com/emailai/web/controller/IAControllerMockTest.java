package com.emailai.web.controller;

import com.emailai.ai.AiService;
import com.emailai.service.MailService;
import com.emailai.service.MensajeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de los endpoints de IA con AiService/MailService/MensajeService mockeados.
 * LM Studio real no se invoca.
 */
@WebMvcTest(controllers = IAController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class IAControllerMockTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private AiService aiService;
    @MockBean private MailService mailService;
    @MockBean private MensajeService mensajeService;

    @Test
    void status_noDisponible_200() throws Exception {
        when(aiService.isAvailable()).thenReturn(false);

        mockMvc.perform(get("/api/ia/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false));
    }

    @Test
    void chat_mensaje_200() throws Exception {
        when(aiService.chat(anyString())).thenReturn("respuesta de la IA");
        when(aiService.isAvailable()).thenReturn(true);

        mockMvc.perform(post("/api/ia/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"hola\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respuesta").value("respuesta de la IA"));
    }

    @Test
    void reentrenar_200() throws Exception {
        doNothing().when(mailService).reentrenarModelo(anyString());

        mockMvc.perform(post("/api/ia/reentrenar").param("cuentaHash", "hashX"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hashX")));
    }
}
