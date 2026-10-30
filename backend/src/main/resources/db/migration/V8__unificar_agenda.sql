-- =====================================================================
-- eMail-IA — V8__unificar_agenda.sql
-- Calendario y Tareas pasan a una sola tabla `agenda`. Lo que se crea en
-- una vista aparece en la otra: el tipo (EVENTO | TAREA) solo decide como
-- se presenta y que integraciones le aplican (ICS todo, Todoist solo TAREA).
-- =====================================================================

CREATE TABLE IF NOT EXISTS agenda (
    id          INTEGER      AUTO_INCREMENT PRIMARY KEY,
    tipo        VARCHAR(10)  NOT NULL DEFAULT 'TAREA',
    titulo      VARCHAR(255) NOT NULL,
    detalle     TEXT,
    fecha       VARCHAR(10),                 -- ISO yyyy-MM-dd (null = tarea sin fecha)
    hora        VARCHAR(5),                  -- HH:mm
    todo_el_dia BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_fin   VARCHAR(10),
    hora_fin    VARCHAR(5),
    estado      TEXT,                        -- pendiente | en_progreso | completada
    prioridad   VARCHAR(10),
    etiquetas   TEXT,
    origen      VARCHAR(20) DEFAULT 'local', -- local | ics | todoist
    mensaje_id  INTEGER,
    todoist_id  VARCHAR(64)
);

-- Eventos existentes
INSERT INTO agenda (tipo, titulo, detalle, fecha, hora, todo_el_dia,
                    fecha_fin, hora_fin, origen, mensaje_id)
SELECT 'EVENTO', titulo, detalle, fecha, hora, todo_el_dia,
       fecha_fin, hora_fin, origen, mensaje_id
FROM eventos_calendario;

-- Tareas existentes: sin hora => todo el dia cuando tengan fecha
INSERT INTO agenda (tipo, titulo, detalle, fecha, todo_el_dia, estado,
                    prioridad, etiquetas, origen, mensaje_id, todoist_id)
SELECT 'TAREA', titulo, descripcion, fecha_vencimiento, TRUE, estado,
       prioridad, etiquetas, 'local', mensaje_id, todoist_id
FROM tareas;

DROP TABLE eventos_calendario;
DROP TABLE tareas;
