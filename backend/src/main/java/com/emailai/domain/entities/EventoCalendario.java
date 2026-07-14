package com.emailai.domain.entities;

import jakarta.persistence.*;

/**
 * Evento del calendario (local o importado de ICS).
 */
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

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(length = 1000)
    private String detalle;

    @Column(length = 20)
    private String origen = "local";             // local | ics

    public EventoCalendario() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public String getHora() { return hora; }
    public void setHora(String hora) { this.hora = hora; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
}
