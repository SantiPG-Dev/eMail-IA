package com.emailai.security;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests de CredentialService con cifrado REAL (bug crítico #6: antes
 * cifrar/descifrar eran la identidad y las credenciales quedaban en claro).
 */
class CredentialServiceTest {

    private static Path tmpDir;
    private static CredentialService service;

    @BeforeAll
    static void prepararCipherKey() throws Exception {
        // CredentialService deriva su clave de DB/cipher.key (ruta relativa);
        // ejecutamos el test desde un directorio temporal con su propia clave.
        tmpDir = Files.createTempDirectory("emailai-cred-test");
        Path dbDir = tmpDir.resolve("DB");
        Files.createDirectories(dbDir);
        byte[] key = new byte[16];
        new java.security.SecureRandom().nextBytes(key);
        Files.write(dbDir.resolve("cipher.key"), key);
        System.setProperty("user.dir", tmpDir.toString());

        // Reset del cache de clave para que derive de la nueva cipher.key
        var campo = CredentialService.class.getDeclaredField("claveCacheada");
        campo.setAccessible(true);
        campo.set(null, null);

        service = new CredentialService();
    }

    @AfterAll
    static void limpiar() throws Exception {
        // Dejar el cache limpio para no afectar a otros tests del mismo JVM
        var campo = CredentialService.class.getDeclaredField("claveCacheada");
        campo.setAccessible(true);
        campo.set(null, null);
    }

    @Test
    void cifrar_descifrar_idaYVuelta() {
        String secreto = "mi-password-IMAP-super-secreta-123!";
        String cifrado = service.cifrar(secreto);

        assertNotEquals(secreto, cifrado);
        assertTrue(cifrado.startsWith(CredentialService.PREFIJO));
        assertEquals(secreto, service.descifrar(cifrado));
    }

    @Test
    void cifrar_noGuardaElPlanoNiEsBase64DelPlano() {
        String secreto = "otra-clave";
        String cifrado = service.cifrar(secreto);

        assertFalse(cifrado.contains(secreto));
        String cuerpo = cifrado.substring(CredentialService.PREFIJO.length());
        assertNotEquals(secreto, new String(Base64.getDecoder().decode(cuerpo)));
    }

    @Test
    void cadaCifradoUsaIVdistinto() {
        String secreto = "mismo-valor";
        String c1 = service.cifrar(secreto);
        String c2 = service.cifrar(secreto);
        assertNotEquals(c1, c2, "IV aleatorio → cifrados distintos del mismo texto");
        assertEquals(secreto, service.descifrar(c1));
        assertEquals(secreto, service.descifrar(c2));
    }

    @Test
    void valorLegacyEnClaro_seDevuelveTalCual() {
        // Migración transparente: datos escritos antes del cifrado
        assertEquals("password-vieja-en-claro", service.descifrar("password-vieja-en-claro"));
        assertNull(service.descifrar(null));
        assertNull(service.descifrar(""));
    }

    @Test
    void manipulacionDelCifrado_falla() {
        String cifrado = service.cifrar("dato-sensible");
        String cuerpo = cifrado.substring(CredentialService.PREFIJO.length());
        byte[] bytes = Base64.getDecoder().decode(cuerpo);
        bytes[bytes.length - 1] ^= 0x01;  // flip de un bit del tag GCM
        String manipulado = CredentialService.PREFIJO
                + Base64.getEncoder().encodeToString(bytes);

        assertThrows(SecurityException.class, () -> service.descifrar(manipulado),
                "GCM debe detectar la manipulación");
    }

    @Test
    void estaCifrado_distingueFormatos() {
        assertTrue(service.estaCifrado(service.cifrar("x")));
        assertFalse(service.estaCifrado("valor-legacy"));
        assertFalse(service.estaCifrado(null));
    }
}
