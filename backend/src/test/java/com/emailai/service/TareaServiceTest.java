package com.emailai.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.domain.entities.Tarea;

@SpringBootTest
@Transactional
class TareaServiceTest {

    @Autowired
    private TareaService tareaService;

    @Test
    void crudTarea() {
        // Crear
        Tarea t = new Tarea();
        t.setTitulo("Comprar leche");
        t.setDescripcion("Ir al supermercado");
        t.setPrioridad("ALTA");
        t.setEstado("pendiente");
        Tarea guardada = tareaService.guardar(t);
        assertNotNull(guardada.getId());

        // Leer
        Tarea encontrada = tareaService.buscarPorId(guardada.getId());
        assertEquals("Comprar leche", encontrada.getTitulo());
        assertEquals("ALTA", encontrada.getPrioridad());

        // Actualizar
        encontrada.setEstado("completada");
        tareaService.actualizar(guardada.getId(), encontrada);
        assertEquals("completada", tareaService.buscarPorId(guardada.getId()).getEstado());

        // Listar
        assertFalse(tareaService.listarTodas().isEmpty());

        // Eliminar
        tareaService.eliminar(guardada.getId());
        assertThrows(Exception.class, () -> tareaService.buscarPorId(guardada.getId()));
    }

    @Test
    void prioridadDefaultMedia() {
        Tarea t = new Tarea();
        t.setTitulo("Tarea sin prioridad");
        Tarea guardada = tareaService.guardar(t);
        assertEquals("MEDIA", guardada.getPrioridad());
    }
}
