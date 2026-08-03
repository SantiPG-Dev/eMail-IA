package com.emailai.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.emailai.domain.entities.Cuenta;
import com.emailai.security.CredentialService;
import com.emailai.security.SecureSessionManager;

// Sincronización automática cada 5 minutos (configurable vía emailai.sync.interval).
// Soporta cuentas con contraseña y OAuth2.
@Service
@EnableScheduling
public class SyncSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SyncSchedulerService.class);

    private volatile String ultimaSync = "-";
    private volatile String estado = "inactivo";

    private final MailService mailService;
    private final CuentaService cuentaService;
    private final CredentialService credentialService;
    private final SecureSessionManager sessionManager;

    public SyncSchedulerService(MailService mailService, CuentaService cuentaService,
                                CredentialService credentialService,
                                SecureSessionManager sessionManager) {
        this.mailService = mailService;
        this.cuentaService = cuentaService;
        this.credentialService = credentialService;
        this.sessionManager = sessionManager;
    }

    /** Devuelve el estado actual del scheduler. */
    public String getEstado() {
        return estado + " · última: " + ultimaSync;
    }

    /**
     * Sincroniza todas las cuentas cada 5 minutos.
     */
    @Scheduled(fixedRateString = "${emailai.sync.interval:300000}")
    public void sincronizarTodasLasCuentas() {
        // No sincronizar si no hay sesión activa (contraseñas cifradas no se pueden descifrar)
        if (!sessionManager.isActive()) {
            log.debug("Sync omitido: sin sesión activa");
            return;
        }
        estado = "sincronizando";
        List<Cuenta> cuentas = cuentaService.listarTodas();
        if (!cuentas.isEmpty()) {
            // pool fijo min(4, n) por ejecución. Suficiente para multi-cuenta sin
            // saturar IMAP. Si hace falta reuso, inyectar un @Bean TaskExecutor.
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, cuentas.size()));
            try {
                CompletableFuture.allOf(
                        cuentas.stream()
                                .map(c -> CompletableFuture.runAsync(() -> syncCuenta(c), pool))
                                .toArray(CompletableFuture[]::new)
                ).join();
            } finally {
                pool.shutdown();
            }
        }
        ultimaSync = java.time.LocalTime.now().toString().substring(0, 5);
        estado = "inactivo";
    }

    /** Sincroniza una sola cuenta (password u OAuth), aislando sus errores. */
    private void syncCuenta(Cuenta cuenta) {
        try {
            if (cuenta.getOauthProvider() != null && cuenta.getOauthAccessToken() != null) {
                syncOAuth(cuenta);
            } else if (cuenta.getUsuarioCifrado() != null && cuenta.getPasswordCifrada() != null) {
                syncPassword(cuenta);
            }
        } catch (Exception e) {
            log.error("Error sincronizando cuenta {}: {}", cuenta.getEmail(), e.getMessage());
        }
    }

    private void syncPassword(Cuenta cuenta) throws Exception {
        String servidor = cuenta.getServidor() != null ? cuenta.getServidor()
                : (cuenta.getEmail().contains("gmail") ? "imap.gmail.com" : "imap.outlook.com");
        int puerto = cuenta.getPuerto() != null ? cuenta.getPuerto() : 993;
        String tipoConexion = cuenta.getTipoConexion() != null ? cuenta.getTipoConexion() : "IMAP";
        var resultados = mailService.sincronizarTodo(servidor,
                cuenta.getEmail(), credentialService.descifrar(cuenta.getPasswordCifrada()), cuenta.getEmail(),
                300, tipoConexion);
        log.info("Sincronizadas {} carpetas para {} ({})", resultados.size(),
                cuenta.getEmail(), tipoConexion);
    }

    private void syncOAuth(Cuenta cuenta) throws Exception {
        String servidor = "imap.gmail.com";
        if ("MICROSOFT".equals(cuenta.getOauthProvider())) {
            servidor = "outlook.office365.com";
        }
        var resultados = mailService.sincronizarTodo(servidor,
                cuenta.getEmail(), credentialService.descifrar(cuenta.getOauthAccessToken()), cuenta.getEmail());
        log.info("Sincronizadas {} carpetas OAuth para {}", resultados.size(), cuenta.getEmail());
    }
}
