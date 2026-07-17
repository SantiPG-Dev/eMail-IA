package com.emailai.domain.entities;

import java.time.LocalDateTime;

import jakarta.persistence.*;

// Cada fila es un ejemplo etiquetado para Weka.
// Features contiene los atributos extraídos serializados.
// Cuanto más entrena el usuario (marcando spam/legítimo), más preciso es el filtro.
@Entity
@Table(name = "entrenamiento")
public class Entrenamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "cuenta_hash", nullable = false, length = 100)
    private String cuentaHash;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String tipo;                          // tipo de modelo

    @Column(columnDefinition = "TEXT")
    private String remitente;

    @Column(columnDefinition = "TEXT")
    private String asunto;

    @Column(columnDefinition = "TEXT")
    private String cuerpo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String etiqueta;                      // LEGITIMO | SPAM | PHISHING

    @Column(columnDefinition = "TEXT")
    private String features;                      // atributos extraídos (serializado)

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Entrenamiento() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getCuentaHash() { return cuentaHash; }
    public void setCuentaHash(String cuentaHash) { this.cuentaHash = cuentaHash; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getRemitente() { return remitente; }
    public void setRemitente(String remitente) { this.remitente = remitente; }
    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }
    public String getCuerpo() { return cuerpo; }
    public void setCuerpo(String cuerpo) { this.cuerpo = cuerpo; }
    public String getEtiqueta() { return etiqueta; }
    public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }
    public String getFeatures() { return features; }
    public void setFeatures(String features) { this.features = features; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
