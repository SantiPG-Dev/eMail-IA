package com.emailai.service;

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

import com.emailai.domain.entities.ItemAgenda;

// Procesa archivos .ics: importa eventos a la agenda unica y exporta
// todo lo que tiene fecha (eventos y tareas). Portado del JavaFX legacy.
@Service
public class IcsService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AgendaService agendaService;

    public IcsService(AgendaService agendaService) {
        this.agendaService = agendaService;
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
        return procesarContenido(Files.readString(path));
    }

    /**
     * Procesa el contenido de un .ics (subida por HTTP) y guarda los eventos.
     */
    @Transactional
    public List<EventoICS> procesarContenido(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            return List.of();
        }
        List<EventoICS> eventos = new ArrayList<>();
        StringBuilder eventoBuilder = new StringBuilder();
        boolean enEvento = false;

        for (String linea : contenido.split("\\r?\\n")) {
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

        // Guardar eventos en la agenda unica (tipo EVENTO)
        for (EventoICS evento : eventos) {
            ItemAgenda e = new ItemAgenda();
            e.setTipo(ItemAgenda.EVENTO);
            e.setFecha(evento.fecha().toString());
            e.setHora(evento.hora() != null ? evento.hora().toString() : null);
            e.setTodoElDia(evento.todoElDia());
            e.setFechaFin(evento.fechaFin() != null ? evento.fechaFin().toString() : null);
            e.setHoraFin(evento.horaFin() != null ? evento.horaFin().toString() : null);
            e.setTitulo(evento.titulo());
            e.setDetalle(evento.detalle());
            e.setOrigen("ics");
            agendaService.guardar(e);
        }

        return eventos;
    }

    private EventoICS parsearEvento(String eventoTexto) {
        String dtStart = extraerCampo(eventoTexto, "DTSTART(?:;[^:]*)?:");
        String dtEnd = extraerCampo(eventoTexto, "DTEND(?:;[^:]*)?:");
        String summary = extraerCampo(eventoTexto, "SUMMARY:");
        String description = extraerCampo(eventoTexto, "DESCRIPTION:");

        if (dtStart == null || summary == null) return null;

        try {
            boolean todoElDia = dtStart.length() == 8;              // solo yyyyMMdd => todo el día
            LocalDate fecha = parsearFecha(dtStart);
            LocalTime hora = todoElDia ? null : parsearHora(dtStart);
            LocalDate fechaFin = null;
            LocalTime horaFin = null;
            String detalle = description != null ? description : "";
            if (dtEnd != null) {
                fechaFin = parsearFecha(dtEnd);
                horaFin = dtEnd.length() > 8 ? parsearHora(dtEnd) : null;
            }
            return new EventoICS(fecha, hora, todoElDia, fechaFin, horaFin, summary, detalle);
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
        Files.writeString(Paths.get(rutaArchivo), generarICS());
    }

    /**
     * Genera el .ics de la agenda con fecha (eventos y tareas) como texto.
     * Sin elementos devuelve un VCALENDAR vacio valido.
     */
    public String generarICS() {
        var eventos = agendaService.listarCalendario();
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//EmailAI//Calendario//EN\nCALSCALE:GREGORIAN\n");
        for (var evento : eventos) {
            sb.append("BEGIN:VEVENT\n");
            sb.append("UID:").append(evento.getId()).append("@emailai\n");
            LocalDate fecha = LocalDate.parse(evento.getFecha());
            LocalTime hora = evento.getHora() != null ? LocalTime.parse(evento.getHora()) : null;
            if (evento.isTodoElDia() || hora == null) {
                // Todo el día (o sin hora): valor DATE, DTEND exclusivo => +1 día
                sb.append("DTSTART;VALUE=DATE:").append(fecha.format(DATE_FORMAT)).append("\n");
                sb.append("DTEND;VALUE=DATE:").append(fecha.plusDays(1).format(DATE_FORMAT)).append("\n");
            } else {
                sb.append("DTSTART:").append(formatearFechaICS(fecha, hora)).append("\n");
                LocalDate fechaFin = evento.getFechaFin() != null ? LocalDate.parse(evento.getFechaFin()) : fecha;
                LocalTime horaFin = evento.getHoraFin() != null ? LocalTime.parse(evento.getHoraFin())
                        : hora.plusHours(1);
                sb.append("DTEND:").append(formatearFechaICS(fechaFin, horaFin)).append("\n");
            }
            sb.append("DTSTAMP:")
              .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")))
              .append("\n");
            sb.append("SUMMARY:").append(escapeTextoICS(evento.getTitulo())).append("\n");
            String detalle = evento.getDetalle() == null ? "" : evento.getDetalle();
            if (ItemAgenda.TAREA.equals(evento.getTipo()) && evento.getEstado() != null) {
                detalle = (detalle + "\nEstado: " + evento.getEstado()).trim();
            }
            if (!detalle.isBlank()) {
                sb.append("DESCRIPTION:").append(escapeTextoICS(detalle)).append("\n");
            }
            sb.append("END:VEVENT\n");
        }
        sb.append("END:VCALENDAR\n");
        return sb.toString();
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
    public record EventoICS(LocalDate fecha, LocalTime hora, boolean todoElDia,
                            LocalDate fechaFin, LocalTime horaFin, String titulo, String detalle) {}
}
