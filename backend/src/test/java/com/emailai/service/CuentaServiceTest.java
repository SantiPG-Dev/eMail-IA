package com.emailai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.domain.entities.Cuenta;

@SpringBootTest
@Transactional
class CuentaServiceTest {

    @Autowired
    private CuentaService cuentaService;

    @Test
    void crudCuenta() {
        Cuenta c = new Cuenta();
        c.setNombre("Gmail");
        c.setEmail("cifrado_user@gmail.com");
        c.setServidor("imap.gmail.com");
        c.setPuerto(993);
        c.setEsDefault(true);

        Cuenta guardada = cuentaService.guardar(c);
        assertNotNull(guardada.getId());
        assertEquals("imap.gmail.com", guardada.getServidor());

        // Buscar por email
        var encontrada = cuentaService.buscarPorEmail("cifrado_user@gmail.com");
        assertTrue(encontrada.isPresent());

        // Default
        var defaultCuenta = cuentaService.buscarDefault();
        assertTrue(defaultCuenta.isPresent());

        // Marcar otra como default
        Cuenta c2 = new Cuenta();
        c2.setNombre("Outlook");
        c2.setEmail("cifrado_outlook@outlook.com");
        cuentaService.guardar(c2);

        cuentaService.marcarComoDefault(c2.getId());

        // Solo una debe ser default
        assertFalse(cuentaService.buscarPorId(guardada.getId()).getEsDefault());
        assertTrue(cuentaService.buscarPorId(c2.getId()).getEsDefault());
    }
}
