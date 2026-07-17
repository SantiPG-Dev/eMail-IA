package com.emailai.security;

import java.util.regex.Pattern;

// Enmascara emails en logs para no mostrar credenciales completas.
public class PrivacyUtil {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("([a-zA-Z0-9+_.-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");

    /**
     * Enmascara un email mostrando solo las primeras 2 letras + dominio.
     * Ejemplo: "usuario@example.com" → "us****@example.com"
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIdx = email.indexOf('@');
        String localPart = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        if (localPart.length() <= 2) {
            return localPart + "****" + domain;
        }
        return localPart.substring(0, 2) + "****" + domain;
    }

    /**
     * Enmascara todos los emails encontrados en un texto.
     */
    public static String sanitize(String text) {
        if (text == null) return null;
        return EMAIL_PATTERN.matcher(text).replaceAll(m -> {
            String email = m.group();
            return maskEmail(email);
        });
    }
}
