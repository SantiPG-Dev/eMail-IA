package com.emailai.oauth;

/**
 * Excepción para errores del flujo OAuth2.
 */
public class OAuth2Exception extends RuntimeException {
    public OAuth2Exception(String message) {
        super(message);
    }
    public OAuth2Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
