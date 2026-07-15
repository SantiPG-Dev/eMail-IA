package com.emailai.domain.entities;

import jakarta.persistence.*;

/**
 * Cuenta de correo (IMAP/SMTP + OAuth2).
 * El email y las credenciales se almacenan cifrados con SecureStorage.
 */
@Entity
@Table(name = "cuentas")
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String email;                        // cifrado

    @Column
    private String servidor;

    @Column
    private Integer puerto;

    @Column(name = "usuario_cifrado")
    private String usuarioCifrado;

    @Column(name = "password_cifrada", length = 500)
    private String passwordCifrada;

    @Column(name = "tipo_conexion", length = 10)
    private String tipoConexion = "IMAP";            // IMAP | POP3

    @Column(name = "es_default")
    private Boolean esDefault = false;

    @Column(name = "oauth_provider", length = 20)
    private String oauthProvider;                // GOOGLE | MICROSOFT | null

    @Column(name = "oauth_access_token", length = 2000)
    private String oauthAccessToken;

    @Column(name = "oauth_refresh_token", length = 2000)
    private String oauthRefreshToken;            // cifrado

    @Column(name = "oauth_expires_at")
    private Long oauthExpiresAt;

    public Cuenta() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getServidor() { return servidor; }
    public void setServidor(String servidor) { this.servidor = servidor; }
    public Integer getPuerto() { return puerto; }
    public void setPuerto(Integer puerto) { this.puerto = puerto; }
    public String getUsuarioCifrado() { return usuarioCifrado; }
    public void setUsuarioCifrado(String usuarioCifrado) { this.usuarioCifrado = usuarioCifrado; }
    public String getPasswordCifrada() { return passwordCifrada; }
    public void setPasswordCifrada(String passwordCifrada) { this.passwordCifrada = passwordCifrada; }
    public String getTipoConexion() { return tipoConexion; }
    public void setTipoConexion(String tipoConexion) { this.tipoConexion = tipoConexion; }
    public Boolean getEsDefault() { return esDefault; }
    public void setEsDefault(Boolean esDefault) { this.esDefault = esDefault; }
    public String getOauthProvider() { return oauthProvider; }
    public void setOauthProvider(String oauthProvider) { this.oauthProvider = oauthProvider; }
    public String getOauthAccessToken() { return oauthAccessToken; }
    public void setOauthAccessToken(String oauthAccessToken) { this.oauthAccessToken = oauthAccessToken; }
    public String getOauthRefreshToken() { return oauthRefreshToken; }
    public void setOauthRefreshToken(String oauthRefreshToken) { this.oauthRefreshToken = oauthRefreshToken; }
    public Long getOauthExpiresAt() { return oauthExpiresAt; }
    public void setOauthExpiresAt(Long oauthExpiresAt) { this.oauthExpiresAt = oauthExpiresAt; }
}
