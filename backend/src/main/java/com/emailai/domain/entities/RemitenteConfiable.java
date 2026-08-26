package com.emailai.domain.entities;

import jakarta.persistence.*;

// Clasificación por remitente: LEGITIMO (nunca spam), SPAM o PHISHING.
// Al forzar una categoría sobre un correo se marca a su remitente aquí.
@Entity
@Table(name = "remitentes_confiables")
public class RemitenteConfiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 255)
    private String remitente;

    @Column(nullable = false)
    private String categoria = "LEGITIMO";

    public RemitenteConfiable() {}

    public RemitenteConfiable(String remitente) {
        this.remitente = remitente;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getRemitente() { return remitente; }
    public void setRemitente(String remitente) { this.remitente = remitente; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
}
