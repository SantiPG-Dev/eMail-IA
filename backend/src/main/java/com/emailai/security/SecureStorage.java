package com.emailai.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cifrado AES-256-GCM para almacenamiento de credenciales y datos sensibles.
 *
 * <p>Portado del legacy JavaFX sin cambios: es Java puro (javax.crypto).
 * La clave se deriva de la contraseña maestra con PBKDF2 (600k iteraciones, OWASP 2023).
 *
 * <p>Formato de salida: {@code Base64(salt + iv + datos_cifrados)}.
 * GCM incluye autenticación integrada (protege contra manipulación).
 */
public class SecureStorage {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final String ALGORITMO_CLAVE = "AES";
    private static final String ALGORITMO_DERIVACION = "PBKDF2WithHmacSHA256";
    private static final int ITERACIONES = 600_000; // OWASP 2023 recommendation
    private static final int LONGITUD_CLAVE = 256;
    private static final int LONGITUD_SALT = 16;
    private static final int LONGITUD_IV = 12;
    private static final int LONGITUD_TAG = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private String masterPassword;

    public SecureStorage(String masterPassword) {
        if (masterPassword == null || masterPassword.isBlank()) {
            throw new IllegalArgumentException("La contraseña maestra no puede estar vacía");
        }
        this.masterPassword = masterPassword;
    }

    public String cifrar(String textoPlano) {
        validarEntrada(textoPlano);
        try {
            byte[] salt = new byte[LONGITUD_SALT];
            RANDOM.nextBytes(salt);

            SecretKey clave = derivarClave(masterPassword, salt);

            byte[] iv = new byte[LONGITUD_IV];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(LONGITUD_TAG, iv));
            byte[] datosCifrados = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));

            byte[] resultado = new byte[salt.length + iv.length + datosCifrados.length];
            System.arraycopy(salt, 0, resultado, 0, salt.length);
            System.arraycopy(iv, 0, resultado, salt.length, iv.length);
            System.arraycopy(datosCifrados, 0, resultado, salt.length + iv.length, datosCifrados.length);

            return Base64.getEncoder().encodeToString(resultado);
        } catch (Exception e) {
            throw new SecurityException("Error al cifrar datos", e);
        }
    }

    public String descifrar(String textoCifrado) {
        if (textoCifrado == null || textoCifrado.isBlank()) {
            throw new IllegalArgumentException("El texto cifrado no puede estar vacío");
        }
        try {
            byte[] datos = Base64.getDecoder().decode(textoCifrado);

            if (datos.length < LONGITUD_SALT + LONGITUD_IV) {
                throw new IllegalArgumentException("Formato de datos cifrados inválido");
            }

            byte[] salt = new byte[LONGITUD_SALT];
            byte[] iv = new byte[LONGITUD_IV];
            byte[] datosCifrados = new byte[datos.length - LONGITUD_SALT - LONGITUD_IV];

            System.arraycopy(datos, 0, salt, 0, LONGITUD_SALT);
            System.arraycopy(datos, LONGITUD_SALT, iv, 0, LONGITUD_IV);
            System.arraycopy(datos, LONGITUD_SALT + LONGITUD_IV, datosCifrados, 0, datosCifrados.length);

            SecretKey clave = derivarClave(masterPassword, salt);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(LONGITUD_TAG, iv));
            byte[] datosDescifrados = cipher.doFinal(datosCifrados);

            return new String(datosDescifrados, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Error al descifrar datos", e);
        }
    }

    /**
     * Genera un hash PBKDF2 del texto para verificación de contraseñas
     * (alternativa a BCrypt para la contraseña maestra).
     */
    public static String hashear(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El texto no puede estar vacío");
        }
        try {
            byte[] salt = new byte[LONGITUD_SALT];
            RANDOM.nextBytes(salt);

            KeySpec spec = new PBEKeySpec(texto.toCharArray(), salt, ITERACIONES, LONGITUD_CLAVE);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITMO_DERIVACION);
            byte[] hash = factory.generateSecret(spec).getEncoded();

            byte[] resultado = new byte[salt.length + hash.length];
            System.arraycopy(salt, 0, resultado, 0, salt.length);
            System.arraycopy(hash, 0, resultado, salt.length, hash.length);

            return Base64.getEncoder().encodeToString(resultado);
        } catch (Exception e) {
            throw new SecurityException("Error al generar hash", e);
        }
    }

    public static boolean verificarHash(String texto, String hashAlmacenado) {
        if (texto == null || hashAlmacenado == null) {
            return false;
        }
        try {
            byte[] datos = Base64.getDecoder().decode(hashAlmacenado);

            if (datos.length < LONGITUD_SALT) {
                return false;
            }

            byte[] salt = new byte[LONGITUD_SALT];
            byte[] hashOriginal = new byte[datos.length - LONGITUD_SALT];

            System.arraycopy(datos, 0, salt, 0, LONGITUD_SALT);
            System.arraycopy(datos, LONGITUD_SALT, hashOriginal, 0, hashOriginal.length);

            KeySpec spec = new PBEKeySpec(texto.toCharArray(), salt, ITERACIONES, LONGITUD_CLAVE);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITMO_DERIVACION);
            byte[] hashCalculado = factory.generateSecret(spec).getEncoded();

            return java.security.MessageDigest.isEqual(hashOriginal, hashCalculado);
        } catch (Exception e) {
            return false;
        }
    }

    private SecretKey derivarClave(String contrasena, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(contrasena.toCharArray(), salt, ITERACIONES, LONGITUD_CLAVE);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITMO_DERIVACION);
            byte[] claveBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(claveBytes, ALGORITMO_CLAVE);
        } catch (Exception e) {
            throw new SecurityException("Error al derivar clave", e);
        }
    }

    private void validarEntrada(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException("El texto no puede ser null");
        }
    }

    /**
     * Limpia la contraseña maestra de memoria.
     */
    public void clear() {
        if (masterPassword != null) {
            char[] chars = masterPassword.toCharArray();
            Arrays.fill(chars, '\0');
            masterPassword = null;
        }
    }
}
