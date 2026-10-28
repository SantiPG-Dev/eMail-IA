-- Leído/no leído local: se marca al abrir un correo desde la app. No toca el
-- flag SEEN del servidor (abriri IMAP en modo escritura por cada lectura no
-- compensa); el upsert del sync no copia este campo, así no se resetea.
ALTER TABLE mensajes
    ADD COLUMN leido BOOLEAN NOT NULL DEFAULT FALSE;
