package com.emailai.domain.entities;

import jakarta.persistence.*;

// Mensaje de correo sincronizado vía IMAP.
// La unique constraint evita duplicados: mismo UID + cuenta + carpeta.
// La clasificación (categoria) la asigna SpamIaService con Weka.
@Entity
@Table(name = "mensajes",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_mensajes_uid_cuenta_carpeta",
           columnNames = {"uid", "cuenta_hash", "carpeta_imap"}))
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String uid;

    @Column(name = "cuenta_hash", nullable = false, columnDefinition = "TEXT")
    private String cuentaHash;

    @Column(name = "carpeta_imap", nullable = false, columnDefinition = "TEXT")
    private String carpetaImap;

    @Column(columnDefinition = "TEXT")
    private String remitente;

    @Column(columnDefinition = "TEXT")
    private String destinatarios;

    @Column(columnDefinition = "TEXT")
    private String cc;

    @Column(columnDefinition = "TEXT")
    private String cco;

    @Column(columnDefinition = "TEXT")
    private String asunto;

    @Column(columnDefinition = "TEXT")
    private String cuerpo;

    @Column(columnDefinition = "TEXT")
    private String html;

    @Column(columnDefinition = "TEXT")
    private String categoria;                    // LEGITIMO | SPAM | PHISHING | DESCONOCIDO

    @Column(columnDefinition = "TEXT")
    private String prioridad;                    // NORMAL | ALTA | URGENTE

    @Column(name = "fecha_recepcion", nullable = false, columnDefinition = "TEXT")
    private String fechaRecepcion;

    public Mensaje() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getCuentaHash() { return cuentaHash; }
    public void setCuentaHash(String cuentaHash) { this.cuentaHash = cuentaHash; }
    public String getCarpetaImap() { return carpetaImap; }
    public void setCarpetaImap(String carpetaImap) { this.carpetaImap = carpetaImap; }
    public String getRemitente() { return remitente; }
    public void setRemitente(String remitente) { this.remitente = remitente; }
    public String getDestinatarios() { return destinatarios; }
    public void setDestinatarios(String destinatarios) { this.destinatarios = destinatarios; }
    public String getCc() { return cc; }
    public void setCc(String cc) { this.cc = cc; }
    public String getCco() { return cco; }
    public void setCco(String cco) { this.cco = cco; }
    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }
    public String getCuerpo() { return cuerpo; }
    public void setCuerpo(String cuerpo) { this.cuerpo = cuerpo; }
    public String getHtml() { return html; }
    public void setHtml(String html) { this.html = html; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }
    public String getFechaRecepcion() { return fechaRecepcion; }
    public void setFechaRecepcion(String fechaRecepcion) { this.fechaRecepcion = fechaRecepcion; }
}
