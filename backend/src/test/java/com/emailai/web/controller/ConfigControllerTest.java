package com.emailai.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void obtenerClave_200() throws Exception {
        mockMvc.perform(get("/api/config")
                        .param("key", "test")
                        .param("defaultValue", "pordefecto"))
                .andExpect(status().isOk());
    }
}
