package com.emailai.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio para procesar archivos .ics y sincronizar eventos con el calendario local.
 * Portado del legacy JavaFX (IcsService.java).
 */
@Service
public class IcsService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final EventoCalendarioService eventoService;

    public IcsService(EventoCalendarioService eventoService) {
        this.eventoService = eventoService;
    }

    /**
     * Procesa un archivo .ics y guarda los eventos en la BD local.
     */
    @Transactional
    public List<EventoICS> procesarArchivoIcs(String rutaArchivo) throws IOException {
        if (!validarRutaICS(rutaArchivo)) {
            throw new SecurityException("Ruta de archivo ICS no permitida: " + rutaArchivo);
        }
        Path path = Paths.get(rutaArchivo);
        if (!Files.exists(path)) {
            throw new IOException("El archivo ICS no existe: " + rutaArchivo);
        }

        List<EventoICS> eventos = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            StringBuilder eventoBuilder = new StringBuilder();
            String linea;
            boolean enEvento = false;

            while ((linea = reader.readLine()) != null) {
                linea = linea.trim();
                if (linea.equals("BEGIN:VEVENT")) {
                    enEvento = true;
                    eventoBuilder.setLength(0);
                    eventoBuilder.append(linea).append("\n");
                } else if (linea.equals("END:VEVENT")) {
                    eventoBuilder.append(linea).append("\n");
                    EventoICS evento = parsearEvento(eventoBuilder.toString());
                    if (evento != null) {
                        eventos.add(evento);
                    }
                    enEvento = false;
                } else if (enEvento) {
                    eventoBuilder.append(linea).append("\n");
                }
            }
        }

        // Guardar eventos en BD
        for (EventoICS evento : eventos) {
            com.emailai.domain.entities.EventoCalendario e = new com.emailai.domain.entities.EventoCalendario();
            e.setFecha(evento.fecha().toString());
            e.setHora(evento.hora() != null ? evento.hora().toString() : null);
            e.setTitulo(evento.titulo());
            e.setDetalle(evento.detalle());
            e.setOrigen("ics");
            eventoService.guardar(e);
        }

        return eventos;
    }

    private EventoICS parsearEvento(String eventoTexto) {
        String dtStart = extraerCampo(eventoTexto, "DTSTART:");
        String dtEnd = extraerCampo(eventoTexto, "DTEND:");
        String summary = extraerCampo(eventoTexto, "SUMMARY:");
        String description = extraerCampo(eventoTexto, "DESCRIPTION:");

        if (dtStart == null || summary == null) return null;

        try {
            LocalDate fecha = parsearFecha(dtStart);
            LocalTime hora = parsearHora(dtStart);
            String detalle = description != null ? description : "";
            if (dtEnd != null) {
                LocalDate fechaFin = parsearFecha(dtEnd);
                LocalTime horaFin = parsearHora(dtEnd);
                detalle += "\n(Fin: " + fechaFin + " " + horaFin + ")";
            }
            return new EventoICS(fecha, hora, summary, detalle);
        } catch (Exception e) {
            return null;
        }
    }

    private String extraerCampo(String texto, String campo) {
        Pattern pattern = Pattern.compile(campo + "([^\\n]*)");
        Matcher matcher = pattern.matcher(texto);
        if (matcher.find()) {
            String valor = matcher.group(1).replaceAll("\\n[ ]+", " ");
            return valor.trim();
        }
        return null;
    }

    private LocalDate parsearFecha(String fechaStr) {
        return LocalDate.parse(fechaStr.substring(0, 8), DATE_FORMAT);
    }

    private LocalTime parsearHora(String horaStr) {
        if (horaStr.length() >= 15 && horaStr.charAt(8) == 'T') {
            return LocalTime.parse(horaStr.substring(9, 15), DateTimeFormatter.ofPattern("HHmmss"));
        }
        return null;
    }

    /**
     * Exporta eventos del calendario local a un archivo .ics.
     */
    public void exportarCalendarioAICS(String rutaArchivo) throws IOException {
        var eventos = eventoService.listarTodos();
        if (eventos.isEmpty()) {
            throw new IOException("No hay eventos para exportar");
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(rutaArchivo))) {
            writer.write("BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//EmailAI//Calendario//EN\nCALSCALE:GREGORIAN\n");
            for (var evento : eventos) {
                writer.write("BEGIN:VEVENT\n");
                writer.write("UID:" + evento.getId() + "@emailai\n");
                String dtStart = formatearFechaICS(LocalDate.parse(evento.getFecha()),
                        evento.getHora() != null ? LocalTime.parse(evento.getHora()) : null);
                writer.write("DTSTART:" + dtStart + "\n");
                writer.write("DTEND:" + dtStart.replaceAll("(\\d{6})", "010000") + "\n");
                writer.write("DTSTAMP:" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")) + "\n");
                writer.write("SUMMARY:" + escapeTextoICS(evento.getTitulo()) + "\n");
                if (evento.getDetalle() != null && !evento.getDetalle().isBlank()) {
                    writer.write("DESCRIPTION:" + escapeTextoICS(evento.getDetalle()) + "\n");
                }
                writer.write("END:VEVENT\n");
            }
            writer.write("END:VCALENDAR\n");
        }
    }

    private String formatearFechaICS(LocalDate fecha, LocalTime hora) {
        if (hora == null) hora = LocalTime.of(0, 0);
        return fecha.format(DATE_FORMAT) + "T" + hora.format(DateTimeFormatter.ofPattern("HHmmss")) + "Z";
    }

    private String escapeTextoICS(String texto) {
        if (texto == null) return "";
        return texto.replace("\\", "\\\\").replace(",", "\\,")
                .replace(";", "\\;").replace("\n", "\\n");
    }

    private boolean validarRutaICS(String ruta) {
        if (ruta == null || ruta.isBlank()) return false;
        Path p = Paths.get(ruta).normalize();
        String pStr = p.toString();
        return pStr.startsWith(System.getProperty("user.home"))
            || pStr.startsWith(System.getProperty("user.dir"))
            || pStr.startsWith(System.getProperty("java.io.tmpdir"));
    }

    /** Resultado de parsear un evento ICS (equivalente al record Evento del legacy). */
    public record EventoICS(LocalDate fecha, LocalTime hora, String titulo, String detalle) {}
}
