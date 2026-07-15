-- =====================================================================
-- eMail-IA — V2__add_tipo_conexion.sql
-- Añade columna tipo_conexion a la tabla cuentas (IMAP | POP3).
-- =====================================================================

ALTER TABLE cuentas ADD COLUMN IF NOT EXISTS tipo_conexion VARCHAR(10) DEFAULT 'IMAP';
