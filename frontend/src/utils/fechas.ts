// Detección de fecha/hora en texto de correos (español) para prefijar
// eventos/tareas creadas desde el correo. Best-effort: si no encuentra
// nada, el llamador usa la fecha del propio correo como fallback.

const MESES = ['enero','febrero','marzo','abril','mayo','junio',
  'julio','agosto','septiembre','octubre','noviembre','diciembre'];
const DIAS = ['domingo','lunes','martes','miercoles','miércoles','jueves','viernes','sabado','sábado'];

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

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
