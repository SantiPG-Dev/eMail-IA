package com.emailai.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.emailai.domain.entities.Entrenamiento;
import com.emailai.domain.entities.Mensaje;

// Clasificación de correo: lista blanca de remitentes primero, luego modelo
// Weka Naive Bayes por cuenta. forzarCategoria() persiste el ejemplo,
// reclasifica en bloque los correos del remitente y reentrena.
@Service
public class ClasificacionCorreoService {

    private static final Logger log = LoggerFactory.getLogger(ClasificacionCorreoService.class);

    private final MensajeService mensajeService;
    private final SpamIaService spamIaService;
    private final RemitenteConfiableService remitenteService;
    private final EntrenamientoService entrenamientoService;

    public ClasificacionCorreoService(MensajeService mensajeService,
                                       SpamIaService spamIaService,
                                       RemitenteConfiableService remitenteService,
                                       EntrenamientoService entrenamientoService) {
        this.mensajeService = mensajeService;
        this.spamIaService = spamIaService;
        this.remitenteService = remitenteService;
        this.entrenamientoService = entrenamientoService;
    }

    public Mensaje clasificarMensaje(Mensaje mensaje) {
        if (mensaje.getCategoria() != null && !"DESCONOCIDO".equals(mensaje.getCategoria())) {
            return mensaje;
        }
        try {
            if (mensaje.getRemitente() != null) {
                // Categoría manual del remitente manda sobre el modelo
                var catRemitente = remitenteService.categoriaDe(mensaje.getRemitente());
                if (catRemitente.isPresent() && !"LEGITIMO".equals(catRemitente.get())) {
                    mensaje.setCategoria(catRemitente.get());
                    return mensaje;
                }
                if (remitenteService.esConfiable(mensaje.getRemitente())) {
                    mensaje.setCategoria("LEGITIMO");
                    return mensaje;
                }
            }
            if (!spamIaService.modeloExiste(mensaje.getCuentaHash())) {
                // Sin modelo entrenado: no se puede clasificar -> indeterminado.
                // (anti-tracking: los DESCONOCIDO no cargan imágenes remotas)
                mensaje.setCategoria("DESCONOCIDO");
                return mensaje;
            }
            SpamIaService.ClaseCorreo clase =
                    spamIaService.clasificar(mensaje.getCuentaHash(), mensaje);
            mensaje.setCategoria(clase.name());
        } catch (Exception e) {
            log.warn("Error clasificando mensaje: {}", e.getMessage());
            mensaje.setCategoria("DESCONOCIDO");
        }
        return mensaje;
    }

    /** Resultado de forzar categoría: el mensaje y cuántos correos del
     *  remitente se reclasificaron en bloque. */
    public record ResultadoForzado(Mensaje mensaje, int reclasificados) {}

    public ResultadoForzado forzarCategoria(Mensaje mensaje, String categoria) {
        // Validar antes de persistir: una categoría fuera del enum se cuela en
        // Entrenamiento y Mensaje y revienta los reentrenamientos futuros.
        if (!SpamIaService.esClaseValida(categoria)) {
            throw new IllegalArgumentException(
                    "Categoría inválida: " + categoria
                    + " (válidas: LEGITIMO, SPAM, PHISHING)");
        }
        String cat = categoria.toUpperCase();
        mensaje.setCategoria(cat);
        Entrenamiento ej = new Entrenamiento();
        ej.setCuentaHash(mensaje.getCuentaHash());
        ej.setTipo("spam");
        ej.setRemitente(mensaje.getRemitente());
        ej.setAsunto(mensaje.getAsunto());
        ej.setCuerpo(mensaje.getCuerpo() != null ? mensaje.getCuerpo() : mensaje.getHtml());
        ej.setEtiqueta(cat);
        ej.setCreatedAt(LocalDateTime.now());
        entrenamientoService.guardar(ej);
        // Clasificar al REMITENTE con la categoría forzada y reclasificar en
        // bloque todos sus correos de la cuenta (el usuario no vuelve a
        // clasificar correo de ese remitente uno a uno).
        int reclasificados = 0;
        if (mensaje.getRemitente() != null && !mensaje.getRemitente().isBlank()) {
            remitenteService.agregar(mensaje.getRemitente(), cat);
            reclasificados = reclasificarRemitente(mensaje.getCuentaHash(), mensaje.getRemitente(), cat);
        }
        reentrenarModeloConEntrenamiento(mensaje.getCuentaHash());
        return new ResultadoForzado(mensaje, reclasificados);
    }

    /** Rescan: aplica la categoría a todos los mensajes del remitente en la
     *  cuenta. Devuelve cuántos cambiaron. */
    private int reclasificarRemitente(String cuentaHash, String remitente, String categoria) {
        int cambiados = 0;
        try {
            for (Mensaje m : mensajeService.listarPorRemitente(cuentaHash, remitente)) {
                if (!categoria.equals(m.getCategoria())) {
                    m.setCategoria(categoria);
                    mensajeService.guardarOActualizar(m);
                    cambiados++;
                }
            }
        } catch (Exception e) {
            log.warn("Error en rescan del remitente {}: {}", remitente, e.getMessage());
        }
        return cambiados;
    }

    public void reentrenarModeloConEntrenamiento(String cuentaHash) {
        try {
            var ejemplos = entrenamientoService.listarPorCuenta(cuentaHash);
            if (ejemplos.isEmpty()) return;
            var mensajes = ejemplos.stream().map(e -> {
                Mensaje m = new Mensaje();
                m.setCuentaHash(e.getCuentaHash());
                m.setRemitente(e.getRemitente());
                m.setAsunto(e.getAsunto());
                m.setCuerpo(e.getCuerpo());
                m.setCategoria(e.getEtiqueta());
                return m;
            }).toList();
            if (mensajes.size() >= 3) {
                spamIaService.entrenarModelo(cuentaHash, mensajes);
                log.info("Modelo reentrenado para cuenta {} con {} ejemplos",
                        cuentaHash, mensajes.size());
            } else {
                log.warn("Reentrenamiento omitido para cuenta {}: {} ejemplos (mín 3)",
                        cuentaHash, mensajes.size());
            }
        } catch (Exception e) {
            log.error("Error reentrenando modelo (conEntrenamiento) cuenta {}", cuentaHash, e);
        }
    }

    public void reentrenarModelo(String cuentaHash) {
        try {
            var mensajes = mensajeService.listarPorCarpeta(cuentaHash, "INBOX");
            // Antes solo se quitaba DESCONOCIDO, pero cualquier categoría fuera
            // del enum (INDETERMINADO de tests, ?categoria=foo histórico...)
            // hacía explotar el setValue. Mejor filtrar por enum válido.
            var entrenamiento = mensajes.stream()
                    .filter(m -> SpamIaService.esClaseValida(m.getCategoria()))
                    .toList();
            if (entrenamiento.size() >= 5) {
                spamIaService.entrenarModelo(cuentaHash, entrenamiento);
                log.info("Modelo reentrenado para cuenta {} con {} ejemplos",
                        cuentaHash, entrenamiento.size());
            } else {
                log.warn("Reentrenamiento omitido para cuenta {}: {} ejemplos válidos (mín 5)",
                        cuentaHash, entrenamiento.size());
            }
        } catch (Exception e) {
            log.error("Error reentrenando modelo cuenta {}", cuentaHash, e);
        }
    }
}
