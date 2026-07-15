package com.emailai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Configura el servidor para servir el frontend React SPA.
 *
 * <p>Sirve archivos estáticos desde {@code frontend/dist/} y redirige
 * cualquier ruta que no sea un archivo real ni una API a {@code index.html}
 * para que React Router maneje la navegación cliente.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.setOrder(-1)  // Prioridad alta para que se ejecute antes que otros handlers
                .addResourceHandler("/**")
                .addResourceLocations("file:../frontend/dist/", "classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws java.io.IOException {
                        // 1. No interceptar rutas de API
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }

                        // 2. Intentar servir el recurso solicitado (JS, CSS, imágenes, etc.)
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }

                        // 3. SPA fallback: servir index.html para todo lo demás
                        //    (rutas como /login, /correo, /config, etc.)
                        Resource index = location.createRelative("index.html");
                        if (index.exists() && index.isReadable()) {
                            return index;
                        }

                        return null;
                    }
                });
    }
}
