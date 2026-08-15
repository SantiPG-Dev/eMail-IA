package com.emailai.web.controller;

import com.emailai.security.CredentialService;
import com.emailai.service.CredencialesMailService;
import com.emailai.service.CuentaService;
import com.emailai.service.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests del envío de correo (SMTP) con los servicios mockeados.
 * SMTP real se prueba con un servidor de correo de integración, no aquí.
 */
@WebMvcTest(controllers = EnviarController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class EnviarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private MailService mailService;
    @MockBean private CuentaService cuentaService;
    @MockBean private CredentialService credentialService;
    @MockBean private CredencialesMailService credencialesMailService;

    @Test
    void enviar_sinCuentaDefault_400() throws Exception {
        when(cuentaService.buscarDefault()).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/enviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"para\":\"x@y.com\",\"asunto\":\"h\",\"cuerpo\":\"c\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enviar_excepcionInterna_500() throws Exception {
        when(cuentaService.buscarDefault()).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/enviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"para\":\"x@y.com\",\"cuerpo\":\"c\"}"))
                .andExpect(status().isInternalServerError());
    }
}
