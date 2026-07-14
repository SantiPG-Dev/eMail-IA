package com.emailai.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.emailai.domain.entities.Cuenta;

/**
 * Servicio de sincronización automática de correo en segundo plano.
 * Revisa periódicamente las cuentas configuradas y sincroniza sus carpetas IMAP.
 */
@Service
@EnableScheduling
public class SyncSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SyncSchedulerService.class);

    private final MailService mailService;
    private final CuentaService cuentaService;

    public SyncSchedulerService(MailService mailService, CuentaService cuentaService) {
        this.mailService = mailService;
        this.cuentaService = cuentaService;
    }

    /**
     * Sincroniza todas las cuentas cada 5 minutos.
     */
    @Scheduled(fixedRateString = "${emailai.sync.interval:300000}")
    public void sincronizarTodasLasCuentas() {
        List<Cuenta> cuentas = cuentaService.listarTodas();
        for (Cuenta cuenta : cuentas) {
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
    }

    private void syncPassword(Cuenta cuenta) throws Exception {
        String servidor = cuenta.getServidor() != null ? cuenta.getServidor()
                : (cuenta.getEmail().contains("gmail") ? "imap.gmail.com" : "imap.outlook.com");
        // Credenciales descifradas serían manejadas en Fase 4 (SecureStorage)
        var resultados = mailService.sincronizarTodo(servidor,
                cuenta.getEmail(), cuenta.getPasswordCifrada(), cuenta.getEmail());
        log.info("Sincronizadas {} carpetas para {}", resultados.size(), cuenta.getEmail());
    }

    private void syncOAuth(Cuenta cuenta) throws Exception {
        String servidor = "imap.gmail.com";
        if ("MICROSOFT".equals(cuenta.getOauthProvider())) {
            servidor = "outlook.office365.com";
        }
        var resultados = mailService.sincronizarTodo(servidor,
                cuenta.getEmail(), cuenta.getOauthAccessToken(), cuenta.getEmail());
        log.info("Sincronizadas {} carpetas OAuth para {}", resultados.size(), cuenta.getEmail());
    }
}
