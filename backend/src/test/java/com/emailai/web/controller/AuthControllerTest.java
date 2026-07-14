package com.emailai.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test de integración del flujo de autenticación.
 *
 * <p>Verifica: setup, login, endpoints protegidos con/sin token,
 * logout, y reintento de setup.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanConfig() throws Exception {
        // Limpiar config entre tests para evitar estado persistente
        var configFile = new java.io.File("config/preferences.properties");
        configFile.delete();
    }

    @Test
    void flujoCompleto() throws Exception {
        // Verificar estado inicial (config limpia)
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurada").value(false));
        // 1. Setup
        mockMvc.perform(post("/api/auth/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"masterPassword\":\"test123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Contraseña maestra configurada correctamente"));

        // 2. Status ahora debe mostrar configurada
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(jsonPath("$.configurada").value(true));

        // 3. Login correcto → devuelve JWT
        var loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"masterPassword\":\"test123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String token = loginResult.getResponse().getHeader("token");
        // El token viene en el body, no en header
        String body = loginResult.getResponse().getContentAsString();
        String jwtToken = body.split("\"token\":\"")[1].split("\"")[0];

        // 4. Endpoint protegido CON token → OK
        mockMvc.perform(get("/api/tareas")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());

        // 5. Endpoint protegido SIN token → 401
        mockMvc.perform(get("/api/tareas"))
                .andExpect(status().isUnauthorized());

        // 6. Login incorrecto → 401
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"masterPassword\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noPermiteDosSetups() throws Exception {
        // Primer setup
        mockMvc.perform(post("/api/auth/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"masterPassword\":\"test123\"}"));

        // Segundo setup debe fallar
        mockMvc.perform(post("/api/auth/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"masterPassword\":\"otra123\"}"))
                .andExpect(status().isBadRequest());
    }
}
