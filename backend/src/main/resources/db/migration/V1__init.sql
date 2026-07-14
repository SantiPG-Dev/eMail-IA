-- =====================================================================
-- eMail-IA — V1__init.sql
-- Esquema inicial consolidado (7 tablas).
--
-- Decisión D1: las 4 BDs H2 del legacy (correos, agenda, contactos, ia)
-- se unifican en una sola BD H2 cifrada para simplificar Spring Data JPA.
-- El esquema es la unión de las tablas creadas por ConexionBD.migrarSiNecesario()
-- y los DAOs individuales del legacy.
--
-- Sintaxis H2 (AUTO_INCREMENT). Hibernate ddl-auto=validate requiere que
-- este esquema coincida con las entidades JPA de la Fase 2.
-- =====================================================================

-- ── cuentas (BD correos del legacy) ──────────────────────────────────
-- Cuentas de correo (IMAP/SMTP + OAuth2). Email y credenciales cifrados.
CREATE TABLE cuentas (
    id                  INTEGER     AUTO_INCREMENT PRIMARY KEY,
    nombre              VARCHAR(100) NOT NULL,
    email               VARCHAR(255) NOT NULL UNIQUE,   -- cifrado con SecureStorage
    servidor            VARCHAR(100),
    puerto              INTEGER,
    usuario_cifrado     VARCHAR(255),
    password_cifrada    VARCHAR(500),
    es_default          BOOLEAN     DEFAULT FALSE,
    oauth_provider      VARCHAR(20) DEFAULT NULL,        -- GOOGLE | MICROSOFT | NULL
    oauth_access_token  VARCHAR(2000) DEFAULT NULL,
    oauth_refresh_token VARCHAR(2000) DEFAULT NULL,     -- cifrado
    oauth_expires_at    BIGINT      DEFAULT NULL
);

-- ── mensajes (BD correos del legacy) ─────────────────────────────────
-- Mensajes de correo sincronizados por IMAP, clasificados por la IA.
CREATE TABLE mensajes (
    id              BIGINT      AUTO_INCREMENT PRIMARY KEY,
    uid             TEXT        NOT NULL,
    cuenta_hash     TEXT        NOT NULL,
    carpeta_imap    TEXT        NOT NULL,
    remitente       TEXT,
    destinatarios   TEXT,
    cc              TEXT,
    cco             TEXT,
    asunto          TEXT,
    cuerpo          TEXT,
    html            TEXT,
    categoria       TEXT,                           -- LEGITIMO | SPAM | PHISHING | DESCONOCIDO
    prioridad       TEXT,                           -- NORMAL | ALTA | URGENTE
    fecha_recepcion TEXT        NOT NULL,
    UNIQUE(uid, cuenta_hash, carpeta_imap)
);

-- ── eventos_calendario (BD agenda del legacy) ────────────────────────
-- Eventos del calendario (locales o importados de ICS).
CREATE TABLE eventos_calendario (
    id      INTEGER     AUTO_INCREMENT PRIMARY KEY,
    fecha   VARCHAR(10) NOT NULL,            -- ISO yyyy-MM-dd
    hora    VARCHAR(5),                      -- HH:mm
    titulo  VARCHAR(255) NOT NULL,
    detalle VARCHAR(1000),
    origen  VARCHAR(20)  DEFAULT 'local'     -- local | ics
);

-- ── tareas (BD agenda del legacy) ────────────────────────────────────
-- Tareas con fechas de vencimiento y sincronización opcional con Todoist.
CREATE TABLE tareas (
    id                INTEGER     AUTO_INCREMENT PRIMARY KEY,
    titulo            VARCHAR(255) NOT NULL,
    descripcion       TEXT,
    fecha_vencimiento TEXT,                     -- ISO yyyy-MM-dd
    estado            TEXT,                     -- pendiente | en_progreso | completada
    etiquetas         TEXT,
    prioridad         VARCHAR(10) DEFAULT 'MEDIA'  -- ALTA | MEDIA | BAJA
);

-- ── contactos (BD contactos del legacy) ──────────────────────────────
-- Contactos locales. Campos sensibles cifrados con SecureStorage.
CREATE TABLE contactos (
    id                INTEGER     AUTO_INCREMENT PRIMARY KEY,
    nombre            VARCHAR(100) NOT NULL,
    apellido_cifrado  TEXT,
    email_cifrado     TEXT,
    telefono_cifrado  TEXT,
    notas_cifrado     TEXT
);

-- ── remitentes_confiables (BD ia del legacy) ─────────────────────────
-- Lista blanca de remitentes (el filtro spam no los marca nunca).
CREATE TABLE remitentes_confiables (
    id        INTEGER     AUTO_INCREMENT PRIMARY KEY,
    remitente VARCHAR(255) NOT NULL UNIQUE
);

-- ── entrenamiento (BD ia del legacy) ─────────────────────────────────
-- Ejemplos de entrenamiento para el clasificador Weka (spam/phishing).
CREATE TABLE entrenamiento (
    id          INTEGER     AUTO_INCREMENT PRIMARY KEY,
    cuenta_hash VARCHAR(100) NOT NULL,
    tipo        TEXT        NOT NULL,          -- tipo de modelo
    remitente   TEXT,
    asunto      TEXT,
    cuerpo      TEXT,
    etiqueta    TEXT        NOT NULL,          -- LEGITIMO | SPAM | PHISHING
    features    TEXT,                           -- atributos extraídos (JSON/serializado)
    created_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);
