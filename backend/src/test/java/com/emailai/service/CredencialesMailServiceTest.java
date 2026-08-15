package com.emailai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.emailai.domain.entities.Cuenta;
import com.emailai.oauth.OAuthService;
import com.emailai.oauth.OAuthService.OAuthTokenResult;
import com.emailai.security.CredentialService;

/**
 * Tests de CredencialesMailService: resolución password vs OAuth2 y
 * refresh del access token caducado (bug crítico #1 del checklist 1.0).
 */
class CredencialesMailServiceTest {

    private final CuentaService cuentaService = mock(CuentaService.class);
    private final CredentialService credentialService = mock(CredentialService.class);
    private final OAuthService oauthService = mock(OAuthService.class);

    private final CredencialesMailService service = new CredencialesMailService(
            cuentaService, credentialService, oauthService);

    private Cuenta cuentaPassword() {
        Cuenta c = new Cuenta();
        c.setEmail("user@gmail.com");
        c.setPasswordCifrada("secreto");
        return c;
    }

    private Cuenta cuentaOAuth(Long expiraEnMs) {
        Cuenta c = new Cuenta();
        c.setEmail("user@gmail.com");
        c.setOauthProvider("GOOGLE");
        c.setOauthAccessToken("access-viejo");
        c.setOauthRefreshToken("refresh-token");
        c.setOauthExpiresAt(System.currentTimeMillis() + (expiraEnMs != null ? expiraEnMs : 3600_000L));
        return c;
    }

    @Test
    void cuentaPassword_sinOAuth_devuelvePassword() {
        when(credentialService.descifrar("secreto")).thenReturn("secreto");

        var cred = service.resolver(cuentaPassword());

        assertNotNull(cred);
        assertEquals("user@gmail.com", cred.user());
        assertEquals("secreto", cred.secret());
        assertFalse(cred.esOAuth());
    }

    @Test
    void cuentaOAuth_tokenVigente_devuelveTokenSinRefresh() {
        when(credentialService.descifrar("access-viejo")).thenReturn("access-viejo");
        when(credentialService.descifrar("refresh-token")).thenReturn("refresh-token");

        var cred = service.resolver(cuentaOAuth(3600_000L));

        assertNotNull(cred);
        assertTrue(cred.esOAuth());
        assertEquals("access-viejo", cred.secret());
        // Token vigente: no se toca OAuthService
        verify(oauthService, never()).renovarToken(anyString(), anyString());
    }

    @Test
    void cuentaOAuth_tokenCaducado_refrescaYPersiste() {
        when(credentialService.descifrar("access-viejo")).thenReturn("access-viejo");
        when(credentialService.descifrar("refresh-token")).thenReturn("refresh-token");
        when(credentialService.cifrar(anyString())).thenAnswer(a -> a.getArgument(0));
        when(oauthService.renovarToken(eq("GOOGLE"), eq("refresh-token")))
                .thenReturn(new OAuthTokenResult("access-nuevo", "refresh-nuevo",
                        System.currentTimeMillis() + 3600_000L));

        var cred = service.resolver(cuentaOAuth(-60_000L)); // caducado hace 1 min

        assertNotNull(cred);
        assertTrue(cred.esOAuth());
        assertEquals("access-nuevo", cred.secret());

        // Persistencia de tokens renovados
        ArgumentCaptor<Cuenta> cap = ArgumentCaptor.forClass(Cuenta.class);
        verify(cuentaService).guardar(cap.capture());
        assertEquals("access-nuevo", cap.getValue().getOauthAccessToken());
        assertEquals("refresh-nuevo", cap.getValue().getOauthRefreshToken());
        assertTrue(cap.getValue().getOauthExpiresAt() > System.currentTimeMillis());
    }

    @Test
    void cuentaOAuth_refreshFallido_conToken_residual_devuelveTokenViejo() {
        when(credentialService.descifrar("access-viejo")).thenReturn("access-viejo");
        when(credentialService.descifrar("refresh-token")).thenReturn("refresh-token");
        when(oauthService.renovarToken(anyString(), anyString()))
                .thenThrow(new RuntimeException("invalid_grant"));

        var cred = service.resolver(cuentaOAuth(-60_000L));

        // Fallback: probar con el token actual antes de rendirse
        assertNotNull(cred);
        assertTrue(cred.esOAuth());
        assertEquals("access-viejo", cred.secret());
    }

    @Test
    void cuentaOAuth_sinTokens_nadaQueHacer() {
        when(credentialService.descifrar(any())).thenReturn(null);

        Cuenta c = cuentaOAuth(null);
        c.setOauthAccessToken(null);
        c.setOauthRefreshToken(null);

        assertNull(service.resolver(c));
        verify(cuentaService, never()).guardar(any());
    }

    @Test
    void cuentaPassword_vacia_devuelveNull() {
        when(credentialService.descifrar(null)).thenReturn(null);

        assertNull(service.resolver(cuentaPassword()));
    }
}
