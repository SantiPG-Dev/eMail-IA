package com.emailai.service;

import com.emailai.domain.entities.Mensaje;
import com.emailai.service.SpamIaService.ClaseCorreo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del clasificador spam (Weka NaiveBayesMultinomial + StringToWordVector).
 * Verifica la lógica core del producto que tuvo bugs recurrentes
 * (Gaussian NB incorrecto, atributo 'clase' duplicado).
 */
class SpamIaServiceTest {

    private Mensaje msg(String categoria, String cuerpo, String asunto) {
        Mensaje m = new Mensaje();
        m.setCategoria(categoria);
        m.setCuerpo(cuerpo);
        m.setAsunto(asunto);
        return m;
    }

    @AfterEach
    void limpiarModelosTest() throws IOException {
        Path dir = Path.of("DB", "ia");
        if (!Files.exists(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().startsWith("modelo_test-"))
                  .forEach(p -> {
                      try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                  });
        }
    }

    @Test
    void sinModeloEntrenado_clasificaLegitimoPorDefecto() throws Exception {
        SpamIaService svc = new SpamIaService();
        String cuenta = "test-inexistente-" + System.nanoTime();
        Mensaje cualquiera = msg(null, "lo que sea", "test");

        assertFalse(svc.modeloExiste(cuenta));
        assertEquals(ClaseCorreo.LEGITIMO, svc.clasificar(cuenta, cualquiera));
    }

    @Test
    void entrenarYClasificar_distingueSpamDeLegitimo() throws Exception {
        SpamIaService svc = new SpamIaService();
        String cuenta = "test-spam-" + System.nanoTime();

        // Vocabulario muy diferenciado entre clases para que NBM separe con pocos ejemplos
        List<Mensaje> entrenamiento = List.of(
                msg("SPAM", "gana dinero gratis casino viagra premio dinero dinero", "has ganado dinero"),
                msg("SPAM", "premio dinero gratis casino viagra oferta regalo dinero", "click gratis"),
                msg("SPAM", "viagra casino dinero gratis premio loteria dinero", "promo dinero"),
                msg("LEGITIMO", "reunion proyecto factura informe cliente reunion", "seguimiento proyecto"),
                msg("LEGITIMO", "factura cliente contrato informe proyecto reunion", "aviso factura"),
                msg("LEGITIMO", "informe proyecto presupuesto cliente reunion factura", "entrega informe")
        );
        svc.entrenarModelo(cuenta, entrenamiento);
        assertTrue(svc.modeloExiste(cuenta), "tras entrenar debe existir el modelo");

        // Mensajes a clasificar reusan palabras vistas en el entrenamiento
        Mensaje spam = msg(null, "gana dinero gratis casino premio", "has ganado dinero");
        Mensaje legit = msg(null, "reunion proyecto factura cliente", "seguimiento proyecto");

        assertEquals(ClaseCorreo.SPAM, svc.clasificar(cuenta, spam),
                "un correo con vocabulario spam debe clasificarse SPAM");
        assertEquals(ClaseCorreo.LEGITIMO, svc.clasificar(cuenta, legit),
                "un correo con vocabulario legitimo debe clasificarse LEGITIMO");
    }

    @Test
    void entrenamientoVacio_noCreaModelo() throws Exception {
        SpamIaService svc = new SpamIaService();
        String cuenta = "test-vacio-" + System.nanoTime();

        svc.entrenarModelo(cuenta, List.of());
        assertFalse(svc.modeloExiste(cuenta));
        assertEquals(ClaseCorreo.LEGITIMO, svc.clasificar(cuenta, msg(null, "x", "y")));
    }

    @Test
    void esClaseValida_validaMayusculasYMinusculas() {
        assertTrue(SpamIaService.esClaseValida("spam"));
        assertTrue(SpamIaService.esClaseValida("LEGITIMO"));
        assertTrue(SpamIaService.esClaseValida("Phishing"));
        assertFalse(SpamIaService.esClaseValida(null));
        assertFalse(SpamIaService.esClaseValida("DESCONOCIDO"));
    }
}
