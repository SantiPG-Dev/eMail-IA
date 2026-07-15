package com.emailai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA fallback para React Router.
 *
 * <p>Registra view controllers para las rutas del frontend SPA.
 * La raíz (/) ya es manejada por WelcomePageHandlerMapping.
 * Las rutas SPA (login, correo, etc.) redirigen a index.html
 * para que React Router maneje la navegación cliente.
 *
 * <p>Los controllers REST (/api/, /health) no se ven afectados
 * porque tienen prioridad (orden 0) sobre estos view controllers.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/login").setViewName("forward:/index.html");
        registry.addViewController("/correo").setViewName("forward:/index.html");
        registry.addViewController("/calendario").setViewName("forward:/index.html");
        registry.addViewController("/contactos").setViewName("forward:/index.html");
        registry.addViewController("/tareas").setViewName("forward:/index.html");
        registry.addViewController("/config").setViewName("forward:/index.html");
        registry.addViewController("/chat-ia").setViewName("forward:/index.html");
    }
}
