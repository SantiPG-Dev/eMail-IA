package com.emailai.security;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.emailai.repository.CuentaRepository;

// Seguridad stateless con JWT Bearer.
// Al ser app de escritorio local, CSRF está desactivado.
// Las rutas /api/auth/** y /health son públicas; el resto de /api/** necesita JWT.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtService jwtService;
    private final CuentaRepository cuentaRepository;

    public SecurityConfig(JwtService jwtService, CuentaRepository cuentaRepository) {
        this.jwtService = jwtService;
        this.cuentaRepository = cuentaRepository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // Sin CSRF porque no es navegador multi-usuario
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/status").permitAll()
                // Login y creación de cuenta inicial son públicos
                .requestMatchers(HttpMethod.GET, "/api/cuentas").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/cuentas").permitAll()
                .requestMatchers("/", "/index.html", "/assets/**", "/src/**", "/*.png", "/*.svg", "/*.ico").permitAll()
                // Rutas SPA (WebConfig hace forward a index.html); el contenido
                // sensible siempre va por /api/** con JWT
                .requestMatchers("/login", "/correo", "/calendario", "/contactos",
                        "/tareas", "/config", "/chat-ia").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/**").authenticated()
                // Denegar por defecto: cualquier endpoint nuevo nace protegido,
                // no expuesto (antes era permitAll por defecto)
                .anyRequest().denyAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"No autenticado\"}");
                })
            )
            .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Filtro inline para validar JWT en cada request a /api/
    private Filter jwtAuthFilter() {
        return new Filter() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request,
                                 jakarta.servlet.ServletResponse response,
                                 FilterChain chain) throws IOException, ServletException {
                HttpServletRequest req = (HttpServletRequest) request;
                String path = req.getRequestURI();

                // Anti DNS-rebinding: un dominio externo que resuelva a
                // 127.0.0.1 enviaría Host: evil.com. Solo se aceptan hosts
                // locales (con puerto opcional); resto → 403.
                String host = req.getHeader("Host");
                if (host != null && !host.isBlank()
                        && !host.matches("^(localhost|127\\.0\\.0\\.1|\\[::1\\])(:\\d+)?$")) {
                    HttpServletResponse bad = (HttpServletResponse) response;
                    bad.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    bad.setContentType("application/json");
                    bad.getWriter().write("{\"error\":\"Host no permitido\"}");
                    return;
                }

                if (!path.startsWith("/api/")) {
                    chain.doFilter(request, response);
                    return;
                }
                if (path.startsWith("/api/auth/") || path.equals("/api/auth")
                        || path.equals("/api/status")
                        // GET /api/cuentas es público: la página de login
                        // necesita listar las cuentas. POST /api/cuentas solo
                        // es público en MODO SETUP (cero cuentas existentes);
                        // después exige JWT para evitar que un proceso local
                        // cree cuentas con servidor IMAP controlado.
                        || (path.equals("/api/cuentas")
                                && (!"POST".equals(req.getMethod())
                                        || cuentaRepository.count() == 0))) {
                    chain.doFilter(request, response);
                    return;
                }

                String authHeader = req.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    try {
                        String token = authHeader.substring(7);
                        String subject = jwtService.extractSubject(token);
                        if (subject != null && jwtService.isValid(token)) {
                            var auth = new UsernamePasswordAuthenticationToken(
                                    subject, null, List.of());
                            SecurityContextHolder.getContext().setAuthentication(auth);
                            chain.doFilter(request, response);
                            return;
                        }
                    } catch (Exception e) {
                        log.warn("Token JWT inválido: {}", e.getMessage());
                    }
                }

                HttpServletResponse resp = (HttpServletResponse) response;
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.setContentType("application/json");
                resp.getWriter().write("{\"error\":\"Token inválido o no proporcionado\"}");
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Solo orígenes locales exactos: backend (8420, sirve el build de
        // producción y el wrapper Electron) y Vite dev (5173).
        // file://* ELIMINADO: permitía que cualquier HTML local abierto por
        // el usuario hiciera requests autenticados al backend.
        config.setAllowedOrigins(List.of(
                "http://localhost:8420", "http://127.0.0.1:8420",
                "http://localhost:5173", "http://127.0.0.1:5173"));
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
