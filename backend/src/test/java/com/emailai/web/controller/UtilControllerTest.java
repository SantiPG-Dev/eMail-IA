package com.emailai.web.controller;

import com.emailai.service.CuentaService;
import com.emailai.service.SyncSchedulerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de utilidades (sync-all) con SyncSchedulerService mockeado.
 * IMAP real no se ejecuta.
 */
@WebMvcTest(controllers = UtilController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class UtilControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private SyncSchedulerService syncService;
    @MockBean private CuentaService cuentaService;

    @Test
    void syncAll_200_yDelegaEnScheduler() throws Exception {
        doNothing().when(syncService).sincronizarTodasLasCuentas();

        mockMvc.perform(post("/api/util/sync-all"))
                .andExpect(status().isOk())
                .andExpect(content().string("Sincronización completa iniciada"));

        verify(syncService).sincronizarTodasLasCuentas();
    }
}
