package com.emailai.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.emailai.domain.entities.ItemAgenda;
import com.emailai.service.AgendaService;
import com.emailai.service.TodoistService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TodoistController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class TodoistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private TodoistService todoistService;
    @MockBean private AgendaService agendaService;

    private ItemAgenda tarea(int id, String titulo) {
        ItemAgenda t = new ItemAgenda();
        t.setId(id);
        t.setTipo(ItemAgenda.TAREA);
        t.setTitulo(titulo);
        return t;
    }

    @Test
    void estado_sinKey_configuradoFalse() throws Exception {
        when(todoistService.isEnabled()).thenReturn(false);
        mockMvc.perform(get("/api/todoist/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurado").value(false));
    }

    @Test
    void probar_sinKey_400() throws Exception {
        when(todoistService.isEnabled()).thenReturn(false);
        mockMvc.perform(post("/api/todoist/probar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false));
    }

    @Test
    void enviarTarea_yaEnviada_409() throws Exception {
        ItemAgenda t = tarea(7, "Comprar pan");
        t.setTodoistId("12345");
        when(agendaService.buscarPorId(7)).thenReturn(t);

        mockMvc.perform(post("/api/todoist/tareas/7"))
                .andExpect(status().isConflict());
    }

    @Test
    void enviarEvento_400() throws Exception {
        ItemAgenda evento = tarea(9, "Reunion");
        evento.setTipo(ItemAgenda.EVENTO);
        when(agendaService.buscarPorId(9)).thenReturn(evento);

        mockMvc.perform(post("/api/todoist/tareas/9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enviarTarea_ok_guardaIdRemoto() throws Exception {
        ItemAgenda t = tarea(8, "Llamar al banco");
        when(agendaService.buscarPorId(8)).thenReturn(t);
        when(todoistService.isEnabled()).thenReturn(true);
        when(todoistService.crearTarea("Llamar al banco", null, "MEDIA")).thenReturn("999");

        mockMvc.perform(post("/api/todoist/tareas/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todoistId").value("999"));
    }
}
