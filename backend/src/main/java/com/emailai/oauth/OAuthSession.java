package com.emailai.oauth;

/**
 * Sesión OAuth2 que captura el resultado de un flujo de autorización.
 */
public class OAuthSession {
    private final String state;
    private final String codeVerifier;
    private final String authorizationUrl;

    public OAuthSession(String state, String codeVerifier, String authorizationUrl) {
        this.state = state;
        this.codeVerifier = codeVerifier;
        this.authorizationUrl = authorizationUrl;
    }

    public String getState() { return state; }
    public String getCodeVerifier() { return codeVerifier; }
    public String getAuthorizationUrl() { return authorizationUrl; }
}
