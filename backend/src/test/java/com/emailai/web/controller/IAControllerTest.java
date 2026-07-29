package com.emailai.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de los endpoints de IA que no requieren conexión a LM Studio.
 * El happy-path de /chat y /reentrenar toca el modelo externo (LM Studio) y
 * se cubre en una pasada futura con @WebMvcTest + @MockBean.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class IAControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void status_200() throws Exception {
        mockMvc.perform(get("/api/ia/status"))
                .andExpect(status().isOk());
    }
}
