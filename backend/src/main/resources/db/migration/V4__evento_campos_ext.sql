-- ── Paridad con apps de calendario/tareas (Evolution/Planify) ────────
-- Eventos: todo-el-día, fin de evento y enlace al correo de origen.
-- Tareas: enlace al correo de origen.
ALTER TABLE eventos_calendario ADD COLUMN todo_el_dia BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE eventos_calendario ADD COLUMN fecha_fin VARCHAR(10);
ALTER TABLE eventos_calendario ADD COLUMN hora_fin VARCHAR(5);
ALTER TABLE eventos_calendario ADD COLUMN mensaje_id INTEGER;

ALTER TABLE tareas ADD COLUMN mensaje_id INTEGER;
