package com.emailai.domain.entities;

import jakarta.persistence.*;

// Adjunto de un correo (bug #5 del checklist 1.0: antes se descartaban).
// El contenido se guarda como BLOB en H2; el listado de mensajes solo
// expone metadatos (nombre/tamaño/tipo) y la descarga lee el BLOB.
@Entity
@Table(name = "adjuntos")
public class Adjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mensaje_id", nullable = false)
    private Mensaje mensaje;

    @Column(nullable = false, length = 512)
    private String nombre;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "BLOB")
    private byte[] datos;

    public Adjunto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Mensaje getMensaje() { return mensaje; }
    public void setMensaje(Mensaje mensaje) { this.mensaje = mensaje; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Long tamanoBytes) { this.tamanoBytes = tamanoBytes; }
    public byte[] getDatos() { return datos; }
    public void setDatos(byte[] datos) { this.datos = datos; }
}
