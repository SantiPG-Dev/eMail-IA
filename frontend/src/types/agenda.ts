// Elemento unico de agenda: lo que crea Calendario aparece en Tareas y al
// reves. `tipo` solo decide como se presenta y que integraciones le aplican.
export type TipoAgenda = 'EVENTO' | 'TAREA';

export interface AgendaItem {
  id: number;
  tipo: TipoAgenda;
  titulo: string;
  detalle: string | null;
  fecha: string | null;
  hora: string | null;
  todoElDia: boolean;
  fechaFin: string | null;
  horaFin: string | null;
  estado: string | null;
  prioridad: string | null;
  etiquetas: string | null;
  origen: string;
  mensajeId: number | null;
  todoistId: string | null;
}

// Lo que aceptan POST/PUT de /api/calendario y /api/tareas (mismo cuerpo).
export interface AgendaPayload {
  titulo: string;
  detalle?: string | null;
  fecha?: string | null;
  hora?: string | null;
  todoElDia?: boolean;
  fechaFin?: string | null;
  horaFin?: string | null;
  estado?: string | null;
  prioridad?: string | null;
  etiquetas?: string | null;
  origen?: string;
  mensajeId?: number | null;
}
