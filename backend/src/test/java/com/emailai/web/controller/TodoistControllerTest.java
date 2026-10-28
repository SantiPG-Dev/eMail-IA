package com.emailai.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.emailai.domain.entities.Tarea;
import com.emailai.service.TareaService;
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
    @MockBean private TareaService tareaService;

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
        Tarea t = new Tarea();
        t.setId(7);
        t.setTitulo("Comprar pan");
        t.setTodoistId("12345");
        when(tareaService.buscarPorId(7)).thenReturn(t);

        mockMvc.perform(post("/api/todoist/tareas/7"))
                .andExpect(status().isConflict());
    }

    @Test
    void enviarTarea_ok_guardaIdRemoto() throws Exception {
        Tarea t = new Tarea();
        t.setId(8);
        t.setTitulo("Llamar al banco");
        when(tareaService.buscarPorId(8)).thenReturn(t);
        when(todoistService.isEnabled()).thenReturn(true);
        when(todoistService.crearTarea("Llamar al banco", null, "MEDIA")).thenReturn("999");

        mockMvc.perform(post("/api/todoist/tareas/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todoistId").value("999"));
    }
}
