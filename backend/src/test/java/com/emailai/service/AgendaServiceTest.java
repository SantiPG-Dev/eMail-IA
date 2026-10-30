package com.emailai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.domain.entities.ItemAgenda;

@SpringBootTest
@Transactional
class AgendaServiceTest {

    @Autowired
    private AgendaService agendaService;

    private ItemAgenda tarea(String titulo, String fecha) {
        ItemAgenda t = new ItemAgenda();
        t.setTitulo(titulo);
        t.setFecha(fecha);
        return t;
    }

    @Test
    void crudTarea() {
        ItemAgenda guardada = agendaService.crearTarea(tarea("Comprar leche", null));
        assertNotNull(guardada.getId());
        assertEquals(ItemAgenda.TAREA, guardada.getTipo());
        assertEquals("MEDIA", guardada.getPrioridad());
        assertEquals("pendiente", guardada.getEstado());

        ItemAgenda encontrada = agendaService.buscarPorId(guardada.getId());
        assertEquals("Comprar leche", encontrada.getTitulo());

        encontrada.setEstado("completada");
        agendaService.actualizarTarea(guardada.getId(), encontrada);
        assertEquals("completada", agendaService.buscarPorId(guardada.getId()).getEstado());

        agendaService.eliminar(guardada.getId());
        assertThrows(Exception.class, () -> agendaService.buscarPorId(guardada.getId()));
    }

    @Test
    void unaTareaConFechaSaleEnCalendarioYUnEventoSaleEnTareas() {
        String fecha = LocalDate.now().plusDays(1).toString();

        ItemAgenda tarea = agendaService.crearTarea(tarea("Pagar factura", fecha));
        assertTrue(agendaService.listarCalendario().stream()
                .anyMatch(i -> i.getId().equals(tarea.getId())),
                "la tarea debe aparecer en el calendario");
        assertTrue(agendaService.listarPorFecha(LocalDate.parse(fecha)).stream()
                .anyMatch(i -> i.getId().equals(tarea.getId())),
                "la tarea debe salir en su dia");

        ItemAgenda evento = agendaService.crearEvento(tarea("Reunion", fecha));
        assertEquals(ItemAgenda.EVENTO, evento.getTipo());
        assertTrue(agendaService.listarTareas().stream()
                .anyMatch(i -> i.getId().equals(evento.getId())),
                "el evento debe aparecer en tareas");
    }

    @Test
    void unaTareaSinFechaNoSaleEnCalendario() {
        ItemAgenda sinFecha = agendaService.crearTarea(tarea("Algun dia", null));
        assertFalse(agendaService.listarCalendario().stream()
                .anyMatch(i -> i.getId().equals(sinFecha.getId())),
                "sin fecha no se puede pintar en el calendario");
        assertTrue(agendaService.listarTareas().stream()
                .anyMatch(i -> i.getId().equals(sinFecha.getId())));
    }

    @Test
    void editarDesdeCalendarioConservaElTipoYElEstadoDeLaTarea() {
        String fecha = LocalDate.now().toString();
        ItemAgenda tarea = agendaService.crearTarea(tarea("Llamar", fecha));
        tarea.setEstado("en_progreso");
        tarea.setEtiquetas("casa");
        agendaService.guardar(tarea);

        ItemAgenda cambios = tarea("Llamar (movida)", LocalDate.now().plusDays(2).toString());
        ItemAgenda editada = agendaService.actualizarEvento(tarea.getId(), cambios);

        assertEquals(ItemAgenda.TAREA, editada.getTipo());
        assertEquals("en_progreso", editada.getEstado());
        assertEquals("casa", editada.getEtiquetas());
        assertEquals(LocalDate.now().plusDays(2).toString(), editada.getFecha());
    }

    @Test
    void editarDesdeTareasConservaElOrigenYElIdDeTodoist() {
        ItemAgenda tarea = agendaService.crearTarea(tarea("Llamar", null));
        tarea.setOrigen("todoist");
        tarea.setTodoistId("999");
        agendaService.guardar(tarea);

        ItemAgenda cambios = tarea("Llamar al banco", null);
        cambios.setEstado("pendiente");
        ItemAgenda editada = agendaService.actualizarTarea(tarea.getId(), cambios);

        assertEquals("todoist", editada.getOrigen());
        assertEquals("999", editada.getTodoistId());
        assertEquals("Llamar al banco", editada.getTitulo());
    }
}
