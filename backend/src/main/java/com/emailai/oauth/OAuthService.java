package com.emailai.oauth;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


// Orquesta el flujo OAuth2 completo: URL de auth → callback local → tokens.
// Las credenciales (clientId, clientSecret) se inyectan desde application.yml
// para que no viajen por la red ni se expongan al frontend.
@Service
public class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CALLBACK_PORT = 9876;

    /** Mapa de states activos (CSRF + anti-replay). */
    public static final ConcurrentHashMap<String, Boolean> ACTIVE_OAUTH_STATES = new ConcurrentHashMap<>();

    /** Estados de un flujo OAuth asíncrono. */
    public static final String FLUJO_PENDIENTE = "PENDIENTE";
    public static final String FLUJO_COMPLETADO = "COMPLETADO";
    public static final String FLUJO_TIMEOUT = "TIMEOUT";
    public static final String FLUJO_ERROR = "ERROR";

    /** Máximo de flujos terminados retenidos en memoria. */
    private static final int MAX_FLUJOS_RETENIDOS = 10;

    /** Flujos OAuth en curso o recién terminados, indexados por id. */
    private final ConcurrentHashMap<String, EstadoFlujo> flujos = new ConcurrentHashMap<>();

    /** Id del flujo con el servidor de callback escuchando (puerto único). */
    private volatile String flujoActivoId = null;

    private final OAuthTokenService tokenService;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String microsoftClientId;
    private final String microsoftClientSecret;

    @org.springframework.beans.factory.annotation.Autowired
    public OAuthService(
            @Value("${emailai.oauth.google.client-id:}") String googleClientId,
            @Value("${emailai.oauth.google.client-secret:}") String googleClientSecret,
            @Value("${emailai.oauth.microsoft.client-id:}") String microsoftClientId,
            @Value("${emailai.oauth.microsoft.client-secret:}") String microsoftClientSecret,
            OAuthTokenService tokenService) {
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.microsoftClientId = microsoftClientId;
        this.microsoftClientSecret = microsoftClientSecret;
        this.tokenService = tokenService;
    }

    /** Atajo para tests: levanta su propio OAuthTokenService con las mismas creds. */
    public OAuthService(
            String googleClientId, String googleClientSecret,
            String microsoftClientId, String microsoftClientSecret) {
        this(googleClientId, googleClientSecret, microsoftClientId, microsoftClientSecret,
                new OAuthTokenService(googleClientId, googleClientSecret,
                        microsoftClientId, microsoftClientSecret));
    }

    /**
     * Genera un state aleatorio para CSRF.
     */
    public String generarState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ACTIVE_OAUTH_STATES.put(state, Boolean.TRUE);
        return state;
    }

    // ── Iniciar flujo ───────────────────────────────────────────

    /**
     * Inicia flujo OAuth con Google.
     * @return URL de autorización para abrir en el navegador
     */
    public String iniciarFlujoGoogle() {
        String state = generarState();
        GoogleOAuthProvider provider = new GoogleOAuthProvider(
                googleClientId, googleClientSecret,
                "http://localhost:" + CALLBACK_PORT + "/oauth/callback");
        return provider.generarUrlAutorizacion(state);
    }

    /**
     * Inicia flujo OAuth con Microsoft.
     */
    public String iniciarFlujoMicrosoft() {
        String state = generarState();
        MicrosoftOAuthProvider provider = new MicrosoftOAuthProvider(
                microsoftClientId, microsoftClientSecret,
                "http://localhost:" + CALLBACK_PORT + "/oauth/callback");
        return provider.generarUrlAutorizacion(state);
    }

    /**
     * Inicia el flujo OAuth según el proveedor.
     */
    public String iniciarFlujo(String proveedor) {
        return switch (proveedor.toUpperCase()) {
            case "GOOGLE" -> iniciarFlujoGoogle();
            case "MICROSOFT" -> iniciarFlujoMicrosoft();
            default -> throw new IllegalArgumentException("Proveedor OAuth no soportado: " + proveedor);
        };
    }

    // ── Flujo asíncrono (no bloquea hilos Tomcat) ───────────────

    /** Flujo iniciado: id para consultar estado + URL de autorización. */
    public record FlujoIniciado(String flujoId, String authUrl) {}

    /** Estado de un flujo: PENDIENTE | COMPLETADO | TIMEOUT | ERROR. */
    public record EstadoFlujo(String estado, OAuthSession session, String error) {}

    /**
     * Inicia el flujo OAuth de forma asíncrona: arranca el servidor de
     * callback en un hilo daemon y devuelve inmediatamente el id del flujo
     * y la URL de autorización. El resultado se consulta con estadoFlujo().
     *
     * Solo puede haber un flujo escuchando a la vez (el callback usa un
     * puerto fijo): si ya hay uno PENDIENTE lanza IllegalStateException.
     */
    public synchronized FlujoIniciado iniciarFlujoAsync(String proveedor) {
        String p = proveedor.toUpperCase();
        if (!p.equals("GOOGLE") && !p.equals("MICROSOFT")) {
            throw new IllegalArgumentException("Proveedor OAuth no soportado: " + proveedor);
        }
        if (flujoActivoId != null) {
            EstadoFlujo activo = flujos.get(flujoActivoId);
            if (activo != null && FLUJO_PENDIENTE.equals(activo.estado())) {
                throw new IllegalStateException(
                        "Ya hay un flujo OAuth en curso — espera a que termine o caduque");
            }
        }

        depurarFlujosTerminados();

        String flujoId = generarFlujoId();
        String authUrl = iniciarFlujo(p);  // genera y registra el state
        flujos.put(flujoId, new EstadoFlujo(FLUJO_PENDIENTE, null, null));
        flujoActivoId = flujoId;

        Thread hilo = new Thread(() -> ejecutarFlujo(flujoId, p), "oauth-flujo-" + flujoId);
        hilo.setDaemon(true);
        hilo.start();

        log.info("Flujo OAuth asíncrono iniciado id={} proveedor={}", flujoId, p);
        return new FlujoIniciado(flujoId, authUrl);
    }

    /**
     * Estado de un flujo. Los tokens solo viajan en COMPLETADO.
     */
    public EstadoFlujo estadoFlujo(String flujoId) {
        EstadoFlujo estado = flujos.get(flujoId);
        if (estado == null) {
            throw new IllegalArgumentException("Flujo OAuth desconocido o expirado: " + flujoId);
        }
        return estado;
    }

    /** Ejecuta espera + canje en background y registra el resultado. */
    private void ejecutarFlujo(String flujoId, String proveedor) {
        try {
            OAuthSession session = esperarCallback(proveedor, 130);
            flujos.put(flujoId, new EstadoFlujo(FLUJO_COMPLETADO, session, null));
            log.info("Flujo OAuth {} completado para {}", flujoId, session.email());
        } catch (TimeoutException e) {
            flujos.put(flujoId, new EstadoFlujo(FLUJO_TIMEOUT, null,
                    "Tiempo de espera agotado para el callback OAuth"));
            log.warn("Flujo OAuth {} timeout", flujoId);
        } catch (IOException | InterruptedException | ExecutionException | OAuth2Exception e) {
            flujos.put(flujoId, new EstadoFlujo(FLUJO_ERROR, null, e.getMessage()));
            log.warn("Flujo OAuth {} error: {}", flujoId, e.getMessage());
        }
    }

    /** Id aleatorio de flujo (mismo formato que el state). */
    private String generarFlujoId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Si hay demasiados flujos retenidos, borra los terminados más viejos. */
    private void depurarFlujosTerminados() {
        if (flujos.size() <= MAX_FLUJOS_RETENIDOS) return;
        flujos.entrySet().removeIf(e -> !FLUJO_PENDIENTE.equals(e.getValue().estado())
                && !e.getKey().equals(flujoActivoId));
    }

    // ── Callback y canje ────────────────────────────────────────

    /**
     * Inicia el servidor callback, espera el resultado, canjea el código por tokens.
     *
     * @param proveedor GOOGLE | MICROSOFT
     * @param timeoutSeconds tiempo máximo de espera
     * @return tokens OAuth obtenidos
     */
    public OAuthSession esperarCallback(String proveedor, int timeoutSeconds)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {

        String tokenUrl = "GOOGLE".equalsIgnoreCase(proveedor)
                ? GoogleOAuthProvider.TOKEN_URL
                : MicrosoftOAuthProvider.TOKEN_URL;
        String clientId = "GOOGLE".equalsIgnoreCase(proveedor) ? googleClientId : microsoftClientId;
        String clientSecret = "GOOGLE".equalsIgnoreCase(proveedor) ? googleClientSecret : microsoftClientSecret;
        String redirectUri = "http://localhost:" + CALLBACK_PORT + "/oauth/callback";

        var server = new OAuthCallbackServer(CALLBACK_PORT);
        server.start();

        try {
            var result = server.waitForCallback(timeoutSeconds);
            String state = result.state();

            // Validar state (anti-CSRF + anti-replay)
            Boolean wasActive = ACTIVE_OAUTH_STATES.remove(state);
            if (wasActive == null || !wasActive) {
                throw new OAuth2Exception("State inválido o ya usado — posible ataque CSRF");
            }

            // Canjear código por tokens
            return tokenService.canjearCodigo(tokenUrl, clientId, clientSecret, redirectUri, result.code(), proveedor);
        } catch (CancellationException | TimeoutException e) {
            server.stop();
            throw new TimeoutException("Tiempo de espera agotado para el callback OAuth");
        }
    }

}
