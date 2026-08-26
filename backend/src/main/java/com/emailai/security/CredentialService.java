package com.emailai.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

// Cifrado REAL de credenciales dentro de H2 (bug crítico #6 del checklist 1.0:
// cifrar()/descifrar() eran la identidad y passwords/tokens OAuth quedaban en
// texto plano en el .mv.db).
//
// Clave: derivada de DB/cipher.key (secreto aleatorio ya existente, el mismo
// que cifra el archivo H2) — derivada UNA vez y cacheada; el PBKDF2 de 600k
// iteraciones de SecureStorage sería demasiado lento para cada sync.
// Formato: "enc1:" + Base64(iv || cifrado+tag GCM). IV aleatorio por cifrado.
//
// Migración transparente: descifrar() devuelve tal cual cualquier valor SIN
// prefijo (dato legacy en claro) — se re-cifra solo la próxima vez que se
// guarde la cuenta. Así no hace falta migración explícita de la BD.
@Service
public class CredentialService {

    static final String PREFIJO = "enc1:";
    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int LONGITUD_IV = 12;
    private static final int LONGITUD_TAG = 128;
    private static final byte[] SAL_DERIVACION =
            "emailai-credential-service-v1".getBytes(StandardCharsets.UTF_8);

    private static volatile SecretKeySpec claveCacheada;

    public String cifrar(String textoPlano) {
        if (textoPlano == null || textoPlano.isBlank()) return textoPlano;
        byte[] plano = textoPlano.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] iv = new byte[LONGITUD_IV];
            java.security.SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, clave(), new GCMParameterSpec(LONGITUD_TAG, iv));
            byte[] cifrado = cipher.doFinal(plano);

            byte[] resultado = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, resultado, 0, iv.length);
            System.arraycopy(cifrado, 0, resultado, iv.length, cifrado.length);

            return PREFIJO + Base64.getEncoder().encodeToString(resultado);
        } catch (Exception e) {
            throw new SecurityException("Error al cifrar credencial", e);
        } finally {
            // La password deja de existir como bytes tan pronto como sea posible
            // (el String original es inmutable, esto es lo único zero-eareable)
            Arrays.fill(plano, (byte) 0);
        }
    }

    public String descifrar(String textoCifrado) {
        if (textoCifrado == null || textoCifrado.isBlank()) return null;
        // Legacy: valor en claro sin prefijo → devolver tal cual (migración al vuelo)
        if (!textoCifrado.startsWith(PREFIJO)) return textoCifrado;
        try {
            byte[] datos = Base64.getDecoder().decode(textoCifrado.substring(PREFIJO.length()));
            if (datos.length < LONGITUD_IV) {
                throw new IllegalArgumentException("Credencial cifrada con formato inválido");
            }
            byte[] iv = Arrays.copyOfRange(datos, 0, LONGITUD_IV);
            byte[] cuerpo = Arrays.copyOfRange(datos, LONGITUD_IV, datos.length);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, clave(), new GCMParameterSpec(LONGITUD_TAG, iv));
            byte[] claro = cipher.doFinal(cuerpo);
            try {
                return new String(claro, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(claro, (byte) 0);
            }
        } catch (Exception e) {
            throw new SecurityException("Error al descifrar credencial", e);
        }
    }

    /** ¿Está el valor cifrado de verdad (prefijo enc1:)? */
    public boolean estaCifrado(String valor) {
        return valor != null && valor.startsWith(PREFIJO);
    }

    /** Clave AES-256 derivada de cipher.key (cacheada tras la primera vez). */
    private static SecretKeySpec clave() {
        SecretKeySpec clave = claveCacheada;
        if (clave != null) return clave;
        synchronized (CredentialService.class) {
            if (claveCacheada != null) return claveCacheada;
            try {
                Path keyFile = com.emailai.config.DatabaseKeyStore.getKeyFilePath();
                byte[] material = Files.readAllBytes(keyFile);
                // Expandir a 256 bits: SHA-256(cipher.key || sal fija)
                byte[] entrada = new byte[material.length + SAL_DERIVACION.length];
                System.arraycopy(material, 0, entrada, 0, material.length);
                System.arraycopy(SAL_DERIVACION, 0, entrada, material.length, SAL_DERIVACION.length);
                byte[] claveBytes = MessageDigest.getInstance("SHA-256").digest(entrada);
                claveCacheada = new SecretKeySpec(claveBytes, "AES");
                // El material de origen ya no hace falta en memoria
                Arrays.fill(material, (byte) 0);
                Arrays.fill(entrada, (byte) 0);
                return claveCacheada;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "No se pudo derivar la clave de credenciales desde cipher.key", e);
            }
        }
    }
}
