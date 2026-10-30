-- Enlace con Todoist: id de la tarea remota para no duplicarla al reenviar.
ALTER TABLE tareas ADD COLUMN todoist_id VARCHAR(64);
