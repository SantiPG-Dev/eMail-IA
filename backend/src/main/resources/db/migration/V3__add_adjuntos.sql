-- Adjuntos de mensajes (bug #5): metadatos + BLOB con el contenido.
CREATE TABLE IF NOT EXISTS adjuntos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mensaje_id BIGINT NOT NULL,
    nombre VARCHAR(512) NOT NULL,
    mime_type VARCHAR(128),
    tamano_bytes BIGINT,
    datos BLOB,
    CONSTRAINT fk_adjunto_mensaje FOREIGN KEY (mensaje_id) REFERENCES mensajes(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_adjuntos_mensaje ON adjuntos(mensaje_id);
