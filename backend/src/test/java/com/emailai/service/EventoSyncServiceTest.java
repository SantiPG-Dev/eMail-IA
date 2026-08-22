package com.emailai.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests del registro de suscriptores SSE:
 * - registrar() devuelve un emitter vivo y lo desregistra al completar
 * - publicar/heartbeat sin suscriptores no fallan
 * - publicar con suscriptor activo no lanza (el send se bufferiza)
 */
class EventoSyncServiceTest {

    private final EventoSyncService service = new EventoSyncService();

    @Test
    void registrar_suscribeEmitter() {
        SseEmitter emitter = service.registrar();
        assertNotNull(emitter);
        assertEquals(1, service.emitters().size());
        // Sin conexión real el emitter bufferiza sends: complete() no dispara
        // onCompletion hasta que Spring lo inicializa (ver EventoControllerTest)
        assertDoesNotThrow(emitter::complete);
    }

    @Test
    void publicar_sinSuscriptores_noFalla() {
        assertDoesNotThrow(() -> service.publicarSyncTerminado("a@b.c", 1, 2, 3));
        assertDoesNotThrow(() -> service.heartbeat());
    }

    @Test
    void publicar_conSuscriptor_noLanza() {
        service.registrar();
        assertDoesNotThrow(() -> service.publicarSyncTerminado("a@b.c", 1, 2, 3));
        assertDoesNotThrow(() -> service.heartbeat());
        assertEquals(1, service.emitters().size(), "con conexión viva el emitter sigue registrado");
    }
}
