package com.emailai.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.domain.entities.Cuenta;
import com.emailai.repository.CuentaRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class CuentaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CuentaRepository cuentaRepository;

    private static final String CUENTA = """
            {"nombre":"Test","email":"test@example.com","servidor":"imap.example.com",
             "puerto":993,"usuario":"","password":"",
             "tipoConexion":"IMAP","esDefault":false}
            """;

    /** Siembra la cuenta sin pasar por el cifrado (CredentialService no arranca en tests). */
    private int crearCuenta() {
        Cuenta c = new Cuenta();
        c.setNombre("Test");
        c.setEmail("test@example.com");
        c.setServidor("imap.example.com");
        c.setPuerto(993);
        c.setTipoConexion("IMAP");
        c.setEsDefault(false);
        return cuentaRepository.save(c).getId();
    }

    @Test
    void listar_vacio_200() throws Exception {
        mockMvc.perform(get("/api/cuentas"))
                .andExpect(status().isOk());
    }

    @Test
    void obtenerInexistente_404() throws Exception {
        mockMvc.perform(get("/api/cuentas/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void actualizar_cambiaNombreSinTocarPassword_200() throws Exception {
        int id = crearCuenta();
        String editada = """
                {"nombre":"Renombrada","email":"test@example.com","servidor":"imap.example.com",
                 "puerto":993,"usuario":"","password":"","tipoConexion":"IMAP","esDefault":false}
                """;
        mockMvc.perform(put("/api/cuentas/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(editada))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Renombrada"));
    }

    @Test
    void actualizar_emailDistinto_400() throws Exception {
        int id = crearCuenta();
        String otroEmail = CUENTA.replace("test@example.com", "otro@example.com");
        mockMvc.perform(put("/api/cuentas/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(otroEmail))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actualizar_inexistente_404() throws Exception {
        mockMvc.perform(put("/api/cuentas/999999")
                        .contentType(MediaType.APPLICATION_JSON).content(CUENTA))
                .andExpect(status().isNotFound());
    }
}
