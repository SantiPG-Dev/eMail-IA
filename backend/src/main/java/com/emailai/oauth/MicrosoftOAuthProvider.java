package com.emailai.oauth;

// Configuración OAuth2 de Microsoft (Outlook/Hotmail).
public class MicrosoftOAuthProvider {

    public static final String AUTH_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
    public static final String TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
    public static final String PROFILE_URL = "https://graph.microsoft.com/v1.0/me";
    public static final String SCOPES = "offline_access IMAP.AccessAsUser.All SMTP.Send";
    public static final String IMAP_HOST = "outlook.office365.com";
    public static final String SMTP_HOST = "smtp.office365.com";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public MicrosoftOAuthProvider(String clientId, String clientSecret, String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public String generarUrlAutorizacion(String state) {
        return AUTH_URL + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=" + SCOPES.replace(" ", "%20")
                + "&state=" + state;
    }

    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getRedirectUri() { return redirectUri; }
    public String getTokenUrl() { return TOKEN_URL; }
    public String getProfileUrl() { return PROFILE_URL; }
}
