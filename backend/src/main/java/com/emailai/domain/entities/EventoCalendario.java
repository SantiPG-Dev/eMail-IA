package com.emailai.domain.entities;

import jakarta.persistence.*;

// Evento del calendario, puede ser local o importado de un fichero ICS.
// Fecha en ISO yyyy-MM-dd, hora en HH:mm, ambas en texto plano.
// Soporta eventos de todo el día (sin hora) y con fin (fecha/hora fin).
@Entity
@Table(name = "eventos_calendario")
public class EventoCalendario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 10)
    private String fecha;                        // ISO yyyy-MM-dd

    @Column(length = 5)
    private String hora;                         // HH:mm

    @Column(nullable = false)
    private boolean todoElDia = false;           // true => ignora hora/horaFin

    @Column(length = 10)
    private String fechaFin;                     // ISO yyyy-MM-dd (opcional)

    @Column(length = 5)
    private String horaFin;                      // HH:mm (opcional)

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(length = 1000)
    private String detalle;

    @Column(length = 20)
    private String origen = "local";             // local | ics

    @Column(name = "mensaje_id")
    private Integer mensajeId;                   // correo de origen (opcional)

    public EventoCalendario() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public String getHora() { return hora; }
    public void setHora(String hora) { this.hora = hora; }
    public boolean isTodoElDia() { return todoElDia; }
    public void setTodoElDia(boolean todoElDia) { this.todoElDia = todoElDia; }
    public String getFechaFin() { return fechaFin; }
    public void setFechaFin(String fechaFin) { this.fechaFin = fechaFin; }
    public String getHoraFin() { return horaFin; }
    public void setHoraFin(String horaFin) { this.horaFin = horaFin; }
    public Integer getMensajeId() { return mensajeId; }
    public void setMensajeId(Integer mensajeId) { this.mensajeId = mensajeId; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
}
