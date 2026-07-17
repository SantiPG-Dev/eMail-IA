package com.emailai.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.emailai.service.CuentaService;
import com.emailai.service.MailService;
import com.emailai.service.MensajeService;
import com.emailai.service.SyncSchedulerService;

// Acciones rápidas: sincronizar todo, reentrenar modelo, etc.
@RestController
@RequestMapping("/api/util")
public class UtilController {

    private final SyncSchedulerService syncService;
    private final CuentaService cuentaService;

    public UtilController(SyncSchedulerService syncService, CuentaService cuentaService) {
        this.syncService = syncService;
        this.cuentaService = cuentaService;
    }

    /** Forzar sincronización de todas las cuentas. */
    @PostMapping("/sync-all")
    public String syncAll() {
        syncService.sincronizarTodasLasCuentas();
        return "Sincronización completa iniciada";
    }
}
