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

/**
 * Configuración de Spring Security para eMail-IA (app de escritorio local).
 *
 * <p>Stateless, usa JWT Bearer token. Se permiten sin autenticación:
 * /health, /api/auth/**. Todo lo demás requiere JWT válido.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // Sin CSRF (app local, no navegador multi-usuario)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/debug/**").permitAll()
                .requestMatchers("/api/status").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/cuentas").permitAll()   // Ver cuentas sin auth (login)
                .requestMatchers(HttpMethod.POST, "/api/cuentas").permitAll()  // Crear cuenta sin auth (primer uso)
                .requestMatchers("/", "/index.html", "/assets/**", "/src/**", "/*.png", "/*.svg", "/*.ico").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
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

    /**
     * Filtro que extrae el token JWT del header Authorization y lo valida.
     */
    private Filter jwtAuthFilter() {
        return new Filter() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request,
                                 jakarta.servlet.ServletResponse response,
                                 FilterChain chain) throws IOException, ServletException {
                HttpServletRequest req = (HttpServletRequest) request;
                String path = req.getRequestURI();

                // Solo proteger rutas /api/ (excepto auth)
                if (!path.startsWith("/api/")) {
                    chain.doFilter(request, response);
                    return;
                }
                if (path.startsWith("/api/auth/") || path.startsWith("/api/debug/") || path.equals("/api/auth")
                        || path.equals("/api/status") || path.equals("/api/cuentas")) {
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

                // API sin token valido
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
        // Solo permitir origenes locales (app de escritorio)
        config.addAllowedOriginPattern("http://localhost:*");
        config.addAllowedOriginPattern("http://127.0.0.1:*");
        config.addAllowedOriginPattern("file://*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
