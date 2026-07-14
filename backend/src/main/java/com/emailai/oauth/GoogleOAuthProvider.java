package com.emailai.oauth;

/**
 * Configuración de OAuth2 para Google.
 */
public class GoogleOAuthProvider {

    public static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    public static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    public static final String PROFILE_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    public static final String SCOPES = "https://mail.google.com/ openid email profile";
    public static final String IMAP_HOST = "imap.gmail.com";
    public static final String SMTP_HOST = "smtp.gmail.com";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GoogleOAuthProvider(String clientId, String clientSecret, String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public String generarUrlAutorizacion(String state) {
        return AUTH_URL + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=" + SCOPES.replace(" ", "%20")
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + state;
    }

    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getRedirectUri() { return redirectUri; }
    public String getTokenUrl() { return TOKEN_URL; }
    public String getProfileUrl() { return PROFILE_URL; }
}
