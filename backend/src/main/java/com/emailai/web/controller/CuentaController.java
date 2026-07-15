package com.emailai.web.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.emailai.domain.entities.Cuenta;
import com.emailai.service.CuentaService;
import com.emailai.service.MailService;
import com.emailai.web.dto.CuentaRequest;
import com.emailai.web.dto.CuentaResponse;

@RestController
@RequestMapping("/api/cuentas")
public class CuentaController {

    private static final Logger log = LoggerFactory.getLogger(CuentaController.class);

    private final CuentaService cuentaService;
    private final MailService mailService;

    public CuentaController(CuentaService cuentaService, MailService mailService) {
        this.cuentaService = cuentaService;
        this.mailService = mailService;
    }

    @GetMapping
    public List<CuentaResponse> listar() {
        return cuentaService.listarTodas().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public CuentaResponse obtener(@PathVariable Integer id) {
        return toResponse(cuentaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<CuentaResponse> crear(@RequestBody CuentaRequest req) {
        Cuenta c = new Cuenta();
        c.setNombre(req.nombre());
        c.setEmail(req.email());          // cifrado en Fase 4
        c.setServidor(req.servidor());
        c.setPuerto(req.puerto());
        c.setUsuarioCifrado(req.usuario());
        c.setPasswordCifrada(req.password());  // cifrado en Fase 4
        c.setTipoConexion(req.tipoConexion() != null ? req.tipoConexion() : "IMAP");
        c.setEsDefault(req.esDefault());
        c.setOauthProvider(req.oauthProvider());
        c.setOauthAccessToken(req.oauthAccessToken());
        c.setOauthRefreshToken(req.oauthRefreshToken());
        c.setOauthExpiresAt(req.oauthExpiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(cuentaService.guardar(c)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        cuentaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/sync")
    public List<?> sincronizar(@PathVariable Integer id,
                                @RequestParam(defaultValue = "300") int limite) throws Exception {
        Cuenta c = cuentaService.buscarPorId(id);
        String servidor = c.getServidor() != null ? c.getServidor()
                : "imap.gmail.com";
        String user = c.getEmail();
        String password = c.getPasswordCifrada();
        if (c.getOauthProvider() != null) {
            password = c.getOauthAccessToken();
        }
        return mailService.sincronizarTodo(servidor, user, password, c.getEmail(), limite);
    }

    /**
     * Lista las carpetas IMAP disponibles para una cuenta.
     * Si falla la conexión, devuelve las carpetas por defecto (INBOX, Sent).
     */
    @GetMapping("/{id}/carpetas")
    public List<Map<String, Object>> listarCarpetas(@PathVariable Integer id) {
        try {
            Cuenta c = cuentaService.buscarPorId(id);
            String servidor = c.getServidor() != null ? c.getServidor()
                    : (c.getEmail() != null && c.getEmail().contains("outlook")
                        ? "outlook.office365.com" : "imap.gmail.com");
            String user = c.getEmail();
            String password = c.getPasswordCifrada();
            if (c.getOauthProvider() != null && c.getOauthAccessToken() != null) {
                password = c.getOauthAccessToken();
            }

            var carpetas = mailService.listarCarpetas(servidor, user, password);
            return carpetas.stream()
                    .map(nombre -> Map.<String, Object>of(
                        "nombre", nombre,
                        "mensajes", 0,
                        "noLeidos", 0
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("No se pudieron listar carpetas IMAP: {}", e.getMessage());
            return List.of(
                Map.<String, Object>of("nombre", "INBOX", "mensajes", 0, "noLeidos", 0),
                Map.<String, Object>of("nombre", "Sent", "mensajes", 0, "noLeidos", 0)
            );
        }
    }

    private CuentaResponse toResponse(Cuenta c) {
        return new CuentaResponse(c.getId(), c.getNombre(), c.getEmail(),
                c.getServidor(), c.getPuerto(),
                c.getTipoConexion() != null ? c.getTipoConexion() : "IMAP",
                c.getEsDefault() != null && c.getEsDefault(), c.getOauthProvider());
    }
}
