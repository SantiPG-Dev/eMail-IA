package com.emailai.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// Push de eventos al frontend vía SSE (GET /api/eventos). El backend avisa
// al terminar cualquier sincronización (manual o del scheduler de 5 min)
// para que la bandeja se recargue al momento, sin polling desde la UI.
@Service
public class EventoSyncService {

    private static final Logger log = LoggerFactory.getLogger(EventoSyncService.class);

    // Emitter sin timeout: la conexión vive mientras la app esté abierta.
    // Los clientes muertos se detectan por error en send() o por heartbeat.
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** Registra un nuevo suscriptor (uno por ventana abierta). */
    public SseEmitter registrar() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        // Evento inicial: fuerza el flush de cabeceras para que el cliente
        // confirme la conexión enseguida (y no cuando llegue el heartbeat).
        enviar(emitter, "conexion", Map.of("ok", true));
        log.debug("Suscriptor SSE registrado (total: {})", emitters.size());
        return emitter;
    }

    /**
     * Notifica que terminó la sincronización de una cuenta, venga del
     * scheduler o de un sync manual (POST /api/cuentas/{id}/sync).
     */
    public void publicarSyncTerminado(String cuenta, int descargados, int totalServer, int noLeidos) {
        if (emitters.isEmpty()) {
            return;
        }
        enviarATodos("sync-terminado", Map.of(
                "cuenta", cuenta,
                "descargados", descargados,
                "totalServer", totalServer,
                "noLeidos", noLeidos));
    }

    // Heartbeat cada 30s: mantiene viva la conexión a través del proxy de
    // Electron (undici corta bodies sin tráfico ~5 min por bodyTimeout) y
    // detecta suscriptores muertos (el send falla y se desregistran).
    @Scheduled(fixedRate = 30000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        enviarATodos("heartbeat", Map.of("ts", System.currentTimeMillis()));
    }

    private void enviarATodos(String evento, Object datos) {
        for (SseEmitter emitter : emitters) {
            enviar(emitter, evento, datos);
        }
    }

    // synchronized por emitter: heartbeat (hilo del scheduler) y eventos de
    // sync (pool del scheduler / hilos de requests) pueden coincidir.
    private void enviar(SseEmitter emitter, String evento, Object datos) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(evento).data(datos));
            }
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
            log.debug("Suscriptor SSE caído ({}): {}", evento, e.getMessage());
        }
    }

    /** Copia de solo lectura de los suscriptores actuales (tests/estado). */
    public List<SseEmitter> emitters() {
        return List.copyOf(emitters);
    }
}
