package com.emailai.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Verifica que el timeout de AiService se aplica DE VERDAD al RestClient
 * (bug crítico #4: timeoutSeconds estaba declarado pero nunca aplicado,
 * con lo que una llamada a LM Studio colgada bloqueaba el hilo para siempre).
 */
class AiServiceTimeoutTest {

    /** Puerto libre efímero para los servidores de prueba. */
    private static int puertoLibre() throws Exception {
        try (var s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    void connectTimeout_aplicado_puertoCerradoFallaRapido() throws Exception {
        int puerto = puertoLibre();  // reservado y liberado: nada escucha ahí
        AiService service = new AiService(
                "http://localhost:" + puerto, "test-model", 5);

        long inicio = System.nanoTime();
        boolean disponible = service.isAvailable();
        long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        assertFalse(disponible, "Puerto cerrado no puede estar disponible");
        // Connect timeout 5s: el fallo debe llegar antes de ~10s,
        // no colgarse indefinidamente como antes del fix.
        assertTrue(ms < 10_000, "La conexión a puerto cerrado tardó " + ms + "ms — timeout no aplicado");
    }

    @Test
    void readTimeout_aplicado_respuestaLentaCortaLaEspera() throws Exception {
        // Servidor que acepta la conexión pero NUNCA responde
        int puerto = puertoLibre();
        AtomicReference<Socket> socketRef = new AtomicReference<>();
        Thread servidor = new Thread(() -> {
            try (var server = new ServerSocket(puerto)) {
                while (!Thread.currentThread().isInterrupted()) {
                    Socket s = server.accept();  // acepta y se queda mudo
                    socketRef.set(s);
                }
            } catch (Exception ignored) {}
        }, "ai-test-silent-server");
        servidor.setDaemon(true);
        servidor.start();
        Thread.sleep(200);  // margen para que levante

        try {
            AiService service = new AiService(
                    "http://localhost:" + puerto, "test-model", 1);  // read timeout 1s

            long inicio = System.nanoTime();
            boolean disponible = service.isAvailable();
            long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

            assertFalse(disponible, "Servidor mudo no puede estar disponible");
            // Read timeout 1s + margen: DEBE fallar en ~1-3s, nunca colgarse
            assertTrue(ms < 5_000, "La lectura del servidor mudo tardó " + ms + "ms — timeout no aplicado");
        } finally {
            servidor.interrupt();
            Socket s = socketRef.get();
            if (s != null) s.close();
        }
    }
}
