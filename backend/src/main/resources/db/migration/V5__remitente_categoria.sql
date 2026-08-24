-- Clasificación por remitente: la lista blanca se convierte en tabla de
-- categorías de remitente (LEGITIMO / SPAM / PHISHING). Los existentes
-- quedan como LEGITIMO. Al forzar una categoría sobre un correo se marca
-- al remitente y se reclasifican en bloque todos sus correos.
ALTER TABLE remitentes_confiables
    ADD COLUMN categoria TEXT NOT NULL DEFAULT 'LEGITIMO';
