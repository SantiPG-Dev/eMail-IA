package com.emailai.domain.entities;

import jakarta.persistence.*;

// Elemento unico de agenda: evento o tarea en la misma tabla.
// Las dos vistas (Calendario y Tareas) leen esta entidad, asi que lo que se
// crea en una aparece en la otra. Fecha en ISO yyyy-MM-dd, hora en HH:mm.
// `fecha` es opcional: una tarea sin vencimiento solo se ve en Tareas.
@Entity
@Table(name = "agenda")
public class ItemAgenda {

    public static final String EVENTO = "EVENTO";
    public static final String TAREA = "TAREA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 10)
    private String tipo = TAREA;                 // EVENTO | TAREA

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String detalle;                      // detalle del evento / descripcion de la tarea

    @Column(length = 10)
    private String fecha;                        // ISO yyyy-MM-dd (null = tarea sin fecha)

    @Column(length = 5)
    private String hora;                         // HH:mm

    @Column(name = "todo_el_dia", nullable = false)
    private boolean todoElDia = false;           // true => ignora hora/horaFin

    @Column(name = "fecha_fin", length = 10)
    private String fechaFin;                     // ISO yyyy-MM-dd (opcional)

    @Column(name = "hora_fin", length = 5)
    private String horaFin;                      // HH:mm (opcional)

    @Column(columnDefinition = "TEXT")
    private String estado;                       // tarea: pendiente | en_progreso | completada

    @Column(length = 10)
    private String prioridad = "MEDIA";          // tarea: ALTA | MEDIA | BAJA

    @Column(columnDefinition = "TEXT")
    private String etiquetas;                    // tarea: "casa, trabajo"

    @Column(length = 20)
    private String origen = "local";             // local | ics | todoist

    @Column(name = "mensaje_id")
    private Integer mensajeId;                   // correo de origen (opcional)

    @Column(name = "todoist_id", length = 64)
    private String todoistId;                    // tarea remota (null = no enviada)

    public ItemAgenda() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
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
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }
    public String getEtiquetas() { return etiquetas; }
    public void setEtiquetas(String etiquetas) { this.etiquetas = etiquetas; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public Integer getMensajeId() { return mensajeId; }
    public void setMensajeId(Integer mensajeId) { this.mensajeId = mensajeId; }
    public String getTodoistId() { return todoistId; }
    public void setTodoistId(String todoistId) { this.todoistId = todoistId; }
}
