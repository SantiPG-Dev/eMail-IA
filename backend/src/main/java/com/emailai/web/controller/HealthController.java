package com.emailai.web.controller;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.emailai.service.SyncSchedulerService;

/**
 * Endpoint de salud y estado del backend.
 *
 * <p>Electron usa {@code GET /health} para saber si el backend está listo.
 * El frontend usa {@code GET /api/status} para mostrar info en la StatusBar.
 */
@RestController
public class HealthController {

    private final Instant inicio = Instant.now();
    private final SyncSchedulerService syncScheduler;

    public HealthController(SyncSchedulerService syncScheduler) {
        this.syncScheduler = syncScheduler;
    }

    /**
     * Health check básico (usado por Electron al arrancar).
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("app", "eMail-IA Backend");
        return status;
    }

    /**
     * Estado detallado del backend para la StatusBar del frontend.
     */
    @GetMapping("/api/status")
    public Map<String, Object> statusDetallado() {
        Map<String, Object> info = new LinkedHashMap<>();

        // Tiempo activo
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        long segundos = runtime.getUptime() / 1000;
        info.put("uptime", formatearDuracion(segundos));

        // Memoria
        MemoryMXBean memoria = ManagementFactory.getMemoryMXBean();
        long usado = memoria.getHeapMemoryUsage().getUsed() / 1024 / 1024;
        long max = memoria.getHeapMemoryUsage().getMax() / 1024 / 1024;
        info.put("memoria", usado + "MB / " + max + "MB");

        // Sync scheduler
        info.put("sync", syncScheduler != null ? syncScheduler.getEstado() : "desconocido");

        return info;
    }

    private String formatearDuracion(long segs) {
        long h = segs / 3600;
        long m = (segs % 3600) / 60;
        long s = segs % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
