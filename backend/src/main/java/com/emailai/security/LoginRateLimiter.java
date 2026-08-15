package com.emailai.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// Rate limiting en memoria para el login: cada intento abre una conexión IMAP
// real contra el servidor de correo (bug crítico #8 del checklist 1.0 — sin
// límite, un bucle martillea el servidor y puede bloquear la cuenta).
//
// Ventana deslizante por email (INTENTOS en VENTANA_MS) + un pequeño
// límite global para que N emails distintos no esquiven el límite por-email.
// Sin dependencias externas: app de escritorio single-user, esto basta.
@Component
public class LoginRateLimiter {

    /** Intentos fallidos máximos por email en la ventana. */
    static final int INTENTOS = 5;
    /** Ventana de intentos por email. */
    static final long VENTANA_MS = 5 * 60 * 1000;
    /** Bloqueo tras agotar los intentos. */
    static final long BLOQUEO_MS = 5 * 60 * 1000;
    /** Intentos globales por minuto (todos los emails juntos). */
    static final int GLOBAL_MINUTO = 20;

    private static final class Historial {
        final Deque<Long> intentos = new ArrayDeque<>();
        volatile long bloqueadoHasta = 0;
    }

    private final ConcurrentHashMap<String, Historial> porEmail = new ConcurrentHashMap<>();
    private final Deque<Long> globales = new ArrayDeque<>();

    /**
     * Registra un intento y dice si está permitido.
     * @param nullReason motivo del rechazo (bloqueado / demasiados intentos)
     * @return true si el intento se permite
     */
    public synchronized boolean permitir(String email, StringBuilder nullReason) {
        long ahora = System.currentTimeMillis();

        // Límite global
        while (!globales.isEmpty() && ahora - globales.peekFirst() > 60_000) {
            globales.pollFirst();
        }
        if (globales.size() >= GLOBAL_MINUTO) {
            nullReason.append("Demasiados intentos de login en total — espera un minuto");
            return false;
        }

        Historial h = porEmail.computeIfAbsent(email, k -> new Historial());

        // Bloqueo activo
        if (h.bloqueadoHasta > ahora) {
            long seg = (h.bloqueadoHasta - ahora) / 1000 + 1;
            nullReason.append("Cuenta bloqueada temporalmente — reintenta en ")
                    .append(seg).append(" s");
            return false;
        }

        // Ventana deslizante por email
        while (!h.intentos.isEmpty() && ahora - h.intentos.peekFirst() > VENTANA_MS) {
            h.intentos.pollFirst();
        }

        globales.addLast(ahora);
        h.intentos.addLast(ahora);

        if (h.intentos.size() > INTENTOS) {
            h.bloqueadoHasta = ahora + BLOQUEO_MS;
            h.intentos.clear();
            nullReason.append("Demasiados intentos fallidos — bloqueado ")
                    .append(BLOQUEO_MS / 60000).append(" minutos");
            return false;
        }
        return true;
    }

    /** Limpia el historial de un email tras un login correcto. */
    public synchronized void reset(String email) {
        porEmail.remove(email);
    }
}
