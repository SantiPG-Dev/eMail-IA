package com.emailai.domain.entities;

import jakarta.persistence.*;

// Lista blanca: remitentes que el filtro spam nunca va a marcar.
// Útil para boletines, notificaciones bancarias, etc.
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
