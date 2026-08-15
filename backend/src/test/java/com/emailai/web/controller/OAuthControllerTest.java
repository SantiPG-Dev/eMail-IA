package com.emailai.web.controller;

import com.emailai.oauth.OAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del flujo OAuth2 asíncrono (iniciar → polling de estado) con
 * OAuthService mockeado. No abre navegador ni llama a Google/Microsoft reales.
 */
@WebMvcTest(controllers = OAuthController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class OAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private OAuthService oauthService;

    @Test
    void iniciar_ok_200_devuelveFlujoIdYAuthUrl() throws Exception {
        when(oauthService.iniciarFlujoAsync("google"))
                .thenReturn(new OAuthService.FlujoIniciado("abc123", "https://accounts.google.com/o/oauth2/auth"));

        mockMvc.perform(post("/api/oauth/iniciar").param("proveedor", "google"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flujoId").value("abc123"))
                .andExpect(jsonPath("$.authUrl").value("https://accounts.google.com/o/oauth2/auth"));
    }

    @Test
    void iniciar_proveedorInvalido_400() throws Exception {
        when(oauthService.iniciarFlujoAsync(anyString()))
                .thenThrow(new IllegalArgumentException("proveedor no soportado"));

        mockMvc.perform(post("/api/oauth/iniciar").param("proveedor", "xxx"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void iniciar_flujoYaEnCurso_409() throws Exception {
        when(oauthService.iniciarFlujoAsync(anyString()))
                .thenThrow(new IllegalStateException("Ya hay un flujo OAuth en curso"));

        mockMvc.perform(post("/api/oauth/iniciar").param("proveedor", "google"))
                .andExpect(status().isConflict());
    }

    @Test
    void estado_pendiente_200() throws Exception {
        when(oauthService.estadoFlujo("abc123"))
                .thenReturn(new OAuthService.EstadoFlujo(OAuthService.FLUJO_PENDIENTE, null, null));

        mockMvc.perform(get("/api/oauth/estado/abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void estado_timeout_408() throws Exception {
        when(oauthService.estadoFlujo("abc123"))
                .thenReturn(new OAuthService.EstadoFlujo(OAuthService.FLUJO_TIMEOUT, null, "agotado"));

        mockMvc.perform(get("/api/oauth/estado/abc123"))
                .andExpect(status().isRequestTimeout())
                .andExpect(jsonPath("$.estado").value("TIMEOUT"));
    }

    @Test
    void estado_desconocido_404() throws Exception {
        when(oauthService.estadoFlujo("noexiste"))
                .thenThrow(new IllegalArgumentException("Flujo OAuth desconocido o expirado: noexiste"));

        mockMvc.perform(get("/api/oauth/estado/noexiste"))
                .andExpect(status().isNotFound());
    }
}
