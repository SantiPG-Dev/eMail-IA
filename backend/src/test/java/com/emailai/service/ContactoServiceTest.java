package com.emailai.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.domain.entities.Contacto;

/**
 * Test de integración del data layer: verifica que las entidades JPA,
 * repositorios y servicios funcionan contra H2 cifrada.
 */
@SpringBootTest
@Transactional
class ContactoServiceTest {

    @Autowired
    private ContactoService contactoService;

    @Test
    void crudContacto() {
        // Crear
        Contacto c = new Contacto();
        c.setNombre("Ana");
        c.setApellidoCifrado("cifrado_apellido");
        c.setEmailCifrado("cifrado_email");
        c.setTelefonoCifrado("cifrado_tel");
        c.setNotasCifrado("cifrado_notas");
        Contacto guardado = contactoService.guardar(c);
        assertNotNull(guardado.getId());

        // Leer
        Contacto encontrado = contactoService.buscarPorId(guardado.getId());
        assertEquals("Ana", encontrado.getNombre());
        assertEquals("cifrado_email", encontrado.getEmailCifrado());

        // Actualizar
        encontrado.setNombre("Ana María");
        contactoService.actualizar(guardado.getId(), encontrado);
        assertEquals("Ana María", contactoService.buscarPorId(guardado.getId()).getNombre());

        // Listar
        assertFalse(contactoService.listarTodos().isEmpty());

        // Eliminar
        contactoService.eliminar(guardado.getId());
        assertThrows(Exception.class, () -> contactoService.buscarPorId(guardado.getId()));
    }
}
