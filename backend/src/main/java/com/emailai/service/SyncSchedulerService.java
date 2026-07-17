package com.emailai.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.emailai.domain.entities.Cuenta;
import com.emailai.security.CredentialService;

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

    public SyncSchedulerService(MailService mailService, CuentaService cuentaService,
                                CredentialService credentialService) {
        this.mailService = mailService;
        this.cuentaService = cuentaService;
        this.credentialService = credentialService;
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
        estado = "sincronizando";
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
        ultimaSync = java.time.LocalTime.now().toString().substring(0, 5);
        estado = "inactivo";
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
