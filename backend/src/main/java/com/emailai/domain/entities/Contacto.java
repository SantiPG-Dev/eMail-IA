package com.emailai.domain.entities;

import jakarta.persistence.*;

// Contacto local. Los campos van cifrados en BD con SecureStorage,
// salvo el nombre que queda en claro para búsquedas básicas.
@Entity
@Table(name = "contactos")
public class Contacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "apellido_cifrado", columnDefinition = "TEXT")
    private String apellidoCifrado;

    @Column(name = "email_cifrado", columnDefinition = "TEXT")
    private String emailCifrado;

    @Column(name = "telefono_cifrado", columnDefinition = "TEXT")
    private String telefonoCifrado;

    @Column(name = "notas_cifrado", columnDefinition = "TEXT")
    private String notasCifrado;

    public Contacto() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getApellidoCifrado() { return apellidoCifrado; }
    public void setApellidoCifrado(String apellidoCifrado) { this.apellidoCifrado = apellidoCifrado; }
    public String getEmailCifrado() { return emailCifrado; }
    public void setEmailCifrado(String emailCifrado) { this.emailCifrado = emailCifrado; }
    public String getTelefonoCifrado() { return telefonoCifrado; }
    public void setTelefonoCifrado(String telefonoCifrado) { this.telefonoCifrado = telefonoCifrado; }
    public String getNotasCifrado() { return notasCifrado; }
    public void setNotasCifrado(String notasCifrado) { this.notasCifrado = notasCifrado; }
}
