package com.emailai.web.controller;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.emailai.ai.AiService;
import com.emailai.service.SyncSchedulerService;

// Health check para Electron (GET /health) y estado detallado para StatusBar (GET /api/status).
// Electron espera 200 OK en /health antes de mostrar la ventana.
// /api/status añade comprobaciones best-effort de subsistemas (H2, LM Studio, Weka):
// nunca deben tirar el endpoint — si algo falla se reporta como "degradado".
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private static final Path MODELOS_DIR = com.emailai.config.DataDir.of("ia");

    private final SyncSchedulerService syncScheduler;
    private final AiService aiService;
    private final JdbcTemplate jdbc;

    public HealthController(SyncSchedulerService syncScheduler, AiService aiService, JdbcTemplate jdbc) {
        this.syncScheduler = syncScheduler;
        this.aiService = aiService;
        this.jdbc = jdbc;
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
     * Estado detallado del backend para la StatusBar del frontend,
     * con comprobaciones de subsistemas (H2, LM Studio, modelos Weka).
     */
    @GetMapping("/api/status")
    public Map<String, Object> statusDetallado() {
        Map<String, Object> info = new LinkedHashMap<>();

        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        info.put("uptime", formatearDuracion(runtime.getUptime() / 1000));

        MemoryMXBean memoria = ManagementFactory.getMemoryMXBean();
        long usado = memoria.getHeapMemoryUsage().getUsed() / 1024 / 1024;
        long max = memoria.getHeapMemoryUsage().getMax() / 1024 / 1024;
        info.put("memoria", usado + "MB / " + max + "MB");

        info.put("sync", syncScheduler != null ? syncScheduler.getEstado() : "desconocido");

        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("h2", comprobarH2());
        checks.put("lmStudio", aiService.isAvailable() ? "disponible" : "no disponible");
        checks.put("weka", Map.of("modelos", contarModelosWeka()));
        info.put("checks", checks);

        return info;
    }

    private String comprobarH2() {
        try {
            Integer uno = jdbc.queryForObject("SELECT 1", Integer.class);
            return uno != null && uno == 1 ? "ok" : "degradado";
        } catch (Exception e) {
            log.warn("Health check H2 falló: {}", e.getMessage());
            return "degradado";
        }
    }

    private long contarModelosWeka() {
        if (!Files.exists(MODELOS_DIR)) return 0;
        // Listar el directorio en O(n); con el nº de cuentas que manejamos va sobrado.
        try (Stream<Path> s = Files.list(MODELOS_DIR)) {
            return s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("modelo_") && n.endsWith(".model");
            }).count();
        } catch (Exception e) {
            return 0;
        }
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
