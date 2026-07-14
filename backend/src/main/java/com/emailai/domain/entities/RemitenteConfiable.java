package com.emailai.domain.entities;

import jakarta.persistence.*;

/**
 * Remitente de confianza (lista blanca). El filtro spam nunca los marca.
 */
@Entity
@Table(name = "remitentes_confiables")
public class RemitenteConfiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 255)
    private String remitente;

    public RemitenteConfiable() {}

    public RemitenteConfiable(String remitente) {
        this.remitente = remitente;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getRemitente() { return remitente; }
    public void setRemitente(String remitente) { this.remitente = remitente; }
}
