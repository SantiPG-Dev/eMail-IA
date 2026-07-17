package com.emailai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// SPA fallback: todas las rutas del frontend React redirigen a index.html
// para que React Router maneje la navegación cliente.
// Los endpoints /api/* y /health no se ven afectados (tienen prioridad).
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
