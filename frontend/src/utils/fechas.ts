// Detección de fecha/hora en texto de correos (español) para prefijar
// eventos/tareas creadas desde el correo. Best-effort: si no encuentra
// nada, el llamador usa la fecha del propio correo como fallback.

const MESES = ['enero','febrero','marzo','abril','mayo','junio',
  'julio','agosto','septiembre','octubre','noviembre','diciembre'];
const DIAS = ['domingo','lunes','martes','miercoles','miércoles','jueves','viernes','sabado','sábado'];

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** Date → 'yyyy-MM-dd' (fecha local, sin desfase UTC). */
export const toIso = iso;

export interface FechaDetectada {
  fecha: string;   // yyyy-MM-dd
  hora: string | null; // HH:mm
}

export function detectarFechaHora(texto: string, fallback: Date = new Date()): FechaDetectada | null {
  if (!texto) return null;
  const t = texto.toLowerCase();
  const base = new Date(fallback.getFullYear(), fallback.getMonth(), fallback.getDate());

  // ISO yyyy-mm-dd
  let m = t.match(/\b(\d{4})-(\d{2})-(\d{2})\b/);
  if (m) return { fecha: `${m[1]}-${m[2]}-${m[3]}`, hora: detectarHora(t) };

  // "25 de agosto (de 2026)"
  m = t.match(/\b(\d{1,2})\s+de\s+([a-záéíóú]+)(?:\s+de\s+(\d{4}))?\b/);
  if (m) {
    const mes = MESES.indexOf(m[2].normalize('NFD').replace(/[\u0300-\u036f]/g, ''));
    if (mes >= 0) {
      const dia = Number(m[1]);
      const anio = m[3] ? Number(m[3]) : base.getFullYear();
      if (dia >= 1 && dia <= 31) {
        return { fecha: `${anio}-${String(mes + 1).padStart(2, '0')}-${String(dia).padStart(2, '0')}`, hora: detectarHora(t) };
      }
    }
  }

  // dd/mm/yyyy o dd/mm (también con guiones)
  m = t.match(/\b(\d{1,2})[/](\d{1,2})(?:[/](\d{2,4}))?\b/);
  if (m) {
    const dia = Number(m[1]), mes = Number(m[2]);
    let anio = m[3] ? Number(m[3]) : base.getFullYear();
    if (m[3] && m[3].length === 2) anio = 2000 + Number(m[3]);
    if (dia >= 1 && dia <= 31 && mes >= 1 && mes <= 12) {
      return { fecha: `${anio}-${String(mes).padStart(2, '0')}-${String(dia).padStart(2, '0')}`, hora: detectarHora(t) };
    }
  }

  // "mañana" / "pasado mañana"
  if (/\bpasado\s+mañana\b/.test(t)) {
    const d = new Date(base); d.setDate(d.getDate() + 2);
    return { fecha: iso(d), hora: detectarHora(t) };
  }
  if (/\bmañana\b/.test(t)) {
    const d = new Date(base); d.setDate(d.getDate() + 1);
    return { fecha: iso(d), hora: detectarHora(t) };
  }

  // Día de semana: próxima ocurrencia a partir de mañana
  const norm = t.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  for (let i = 0; i < DIAS.length; i += 2) { // DIAS alterna sin/con tilde
    const sin = DIAS[i], con = DIAS[i + 1];
    const re = new RegExp(`\\b(?:${sin}|${con})\\b`);
    if (re.test(norm)) {
      const objetivo = i / 2; // 0=domingo
      const d = new Date(base); d.setDate(d.getDate() + 1);
      while (d.getDay() !== objetivo) d.setDate(d.getDate() + 1);
      return { fecha: iso(d), hora: detectarHora(t) };
    }
  }

  return null;
}

function detectarHora(t: string): string | null {
  const m = t.match(/(?:\ba\s+las\s+|\ba\s+l\b|\bhoras?\s+|\b)?([01]?\d|2[0-3]):([0-5]\d)\s*(?:h|hrs|horas)?\b/);
  return m ? `${m[1].padStart(2, '0')}:${m[2]}` : null;
}

// ── Vista agenda (próximos eventos) ──────────────────────────────

export interface EventoAgenda {
  fecha: string; fechaFin?: string | null; hora?: string | null;
  todoElDia?: boolean;
}

// Día bajo el que se muestra el evento en la agenda: los multi-día que
// empezaron antes pero siguen vigentes se cuelgan de "Hoy".
export function diaAgenda(e: EventoAgenda, hoy: string): string {
  return e.fecha < hoy ? hoy : e.fecha;
}

// Orden natural de eventos dentro de un día: todo-el-día primero, luego hora.
export function compararEventos(a: EventoAgenda, b: EventoAgenda): number {
  return Number(b.todoElDia ?? false) - Number(a.todoElDia ?? false)
      || (a.hora ?? '99:99').localeCompare(b.hora ?? '99:99');
}

// Eventos de hoy en adelante (incluye los que empezaron antes pero
// aún no terminan), ordenados por día, todo-el-día primero y luego hora.
export function proximosEventos<T extends EventoAgenda>(eventos: T[], hoy: string, limite = 50): T[] {
  return eventos
    .filter(e => (e.fechaFin || e.fecha) >= hoy)
    .sort((a, b) =>
      diaAgenda(a, hoy).localeCompare(diaAgenda(b, hoy))
      || compararEventos(a, b))
    .slice(0, limite);
}

// "Hoy" | "Mañana" | "martes, 25 de agosto"
export function etiquetaFecha(fecha: string, hoy: Date = new Date()): string {
  if (fecha === toIso(hoy)) return 'Hoy';
  const manana = new Date(hoy); manana.setDate(hoy.getDate() + 1);
  if (fecha === toIso(manana)) return 'Mañana';
  const d = new Date(fecha + 'T12:00:00');
  return `${DIAS_CAP[d.getDay()]}, ${d.getDate()} de ${MESES_CAP[d.getMonth()]}`;
}

const DIAS_CAP = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
const MESES_CAP = ['enero','febrero','marzo','abril','mayo','junio',
  'julio','agosto','septiembre','octubre','noviembre','diciembre'];
