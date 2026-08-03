package com.emailai.web.controller;

import com.emailai.oauth.OAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del flujo OAuth2 (inicio + callback) con OAuthService mockeado.
 * No abre navegador ni llama a Google/Microsoft reales.
 */
@WebMvcTest(controllers = OAuthController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class OAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private OAuthService oauthService;

    @Test
    void iniciar_ok_200() throws Exception {
        when(oauthService.iniciarFlujo("google"))
                .thenReturn("https://accounts.google.com/o/oauth2/auth");

        mockMvc.perform(post("/api/oauth/iniciar").param("proveedor", "google"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authUrl").value("https://accounts.google.com/o/oauth2/auth"));
    }

    @Test
    void iniciar_proveedorInvalido_400() throws Exception {
        when(oauthService.iniciarFlujo(anyString()))
                .thenThrow(new IllegalArgumentException("proveedor no soportado"));

        mockMvc.perform(post("/api/oauth/iniciar").param("proveedor", "xxx"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void callback_timeout_408() throws Exception {
        when(oauthService.esperarCallback(anyString(), anyInt()))
                .thenThrow(new java.util.concurrent.TimeoutException());

        mockMvc.perform(post("/api/oauth/callback").param("proveedor", "google"))
                .andExpect(status().isRequestTimeout());
    }
}
