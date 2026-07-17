package com.emailai.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.emailai.oauth.OAuthService;
import com.emailai.service.CuentaService;

// Inicio de flujo OAuth2 (redirige a Google/Microsoft) y callback.
@RestController
@RequestMapping("/api/oauth")
public class OAuthController {

    private final OAuthService oauthService;
    private final CuentaService cuentaService;

    public OAuthController(OAuthService oauthService, CuentaService cuentaService) {
        this.oauthService = oauthService;
        this.cuentaService = cuentaService;
    }

    @PostMapping("/google/iniciar")
    public String iniciarGoogle(@RequestParam String clientId, @RequestParam String clientSecret) {
        return oauthService.iniciarFlujoGoogle(clientId, clientSecret);
    }

    @PostMapping("/microsoft/iniciar")
    public String iniciarMicrosoft(@RequestParam String clientId, @RequestParam String clientSecret) {
        return oauthService.iniciarFlujoMicrosoft(clientId, clientSecret);
    }
}
