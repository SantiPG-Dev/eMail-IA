package com.emailai.domain.entities;

import jakarta.persistence.*;

/**
 * Tarea con fechas de vencimiento y sincronización opcional con Todoist.
 */
@Entity
@Table(name = "tareas")
public class Tarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "fecha_vencimiento", columnDefinition = "TEXT")
    private String fechaVencimiento;              // ISO yyyy-MM-dd

    @Column(columnDefinition = "TEXT")
    private String estado;                        // pendiente | en_progreso | completada

    @Column(columnDefinition = "TEXT")
    private String etiquetas;

    @Column(length = 10)
    private String prioridad = "MEDIA";           // ALTA | MEDIA | BAJA

    public Tarea() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(String fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getEtiquetas() { return etiquetas; }
    public void setEtiquetas(String etiquetas) { this.etiquetas = etiquetas; }
    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }
}
