package com.emailai.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests del filtro JWT y la cadena de seguridad:
 * - rutas públicas (/api/auth/**, /api/status) accesibles sin token
 * - rutas /api/** protegidas → 401 sin token
 * - rutas /api/** con token válido → no 401 (el filtro autentica)
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void rutaPublica_status_sinToken_200() throws Exception {
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk());
    }

    @Test
    void rutaProtegida_sinToken_401() throws Exception {
        mockMvc.perform(get("/api/mensajes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rutaProtegida_conTokenValido_no401() throws Exception {
        String token = jwtService.generateToken("user@test.com");
        MvcResult res = mockMvc.perform(get("/api/mensajes")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        assertNotEquals(401, res.getResponse().getStatus(),
                "con token válido el filtro debe autenticar y no devolver 401");
    }

    @Test
    void rutaProtegida_conTokenBasura_401() throws Exception {
        mockMvc.perform(get("/api/mensajes")
                        .header("Authorization", "Bearer no-es-un-jwt-valido"))
                .andExpect(status().isUnauthorized());
    }
}
