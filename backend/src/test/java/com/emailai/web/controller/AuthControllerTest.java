package com.emailai.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.emailai.service.CuentaService;

/**
 * Test del flujo de autenticación sin contraseña maestra.
 *
 * <p>La autenticación ahora se hace contra IMAP (no hay master password).
 * Login real requiere servidor IMAP y no se testea aquí.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CuentaService cuentaService;

    @Test
    void loginSinCuentasDevuelveNotFound() throws Exception {
        // Sin cuentas configuradas → login devuelve 404
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nadie@test.com\",\"masterPassword\":\"pass\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusSinCuentas() throws Exception {
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurada").value(false))
                .andExpect(jsonPath("$.sesionActiva").value(false));
    }

    @Test
    void logoutSinSesion() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());
    }

    @Test
    void loginSinEmail() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"masterPassword\":\"pass\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginSinPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@test.com\",\"masterPassword\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
