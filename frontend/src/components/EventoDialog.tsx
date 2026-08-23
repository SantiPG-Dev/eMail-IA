import { useState } from 'react';
import { eventoApi } from '../api/client';

export interface Evento {
  id: number; fecha: string; hora: string | null;
  todoElDia: boolean; fechaFin: string | null; horaFin: string | null;
  titulo: string; detalle: string | null; origen: string; mensajeId: number | null;
}

// Diálogo para crear/editar eventos del calendario.
// Modo edición si llega `evento`; si no, creación (con prefijados del correo).
interface EventoDialogProps {
  open: boolean;
  fecha?: string;                 // fecha inicial al crear
  evento?: Evento | null;         // evento a editar
  prefill?: { titulo?: string; detalle?: string; hora?: string; mensajeId?: number };
  onClose: () => void;
  onSaved: () => void;
}

export default function EventoDialog({ open, fecha, evento, prefill, onClose, onSaved }: EventoDialogProps) {
  const [titulo, setTitulo] = useState(evento?.titulo ?? prefill?.titulo ?? '');
  const [detalle, setDetalle] = useState(evento?.detalle ?? prefill?.detalle ?? '');
  const [fechaIni, setFechaIni] = useState(evento?.fecha ?? fecha ?? new Date().toISOString().slice(0, 10));
  const [hora, setHora] = useState(evento?.hora ?? prefill?.hora ?? '');
  const [todoElDia, setTodoElDia] = useState(evento?.todoElDia ?? false);
  const [fechaFin, setFechaFin] = useState(evento?.fechaFin ?? '');
  const [horaFin, setHoraFin] = useState(evento?.horaFin ?? '');
  const [status, setStatus] = useState('');

  if (!open) return null;

  const guardar = async () => {
    if (!titulo.trim()) { setStatus('El título es obligatorio'); return; }
    const data = {
      fecha: fechaIni,
      hora: todoElDia ? null : (hora || null),
      todoElDia,
      fechaFin: fechaFin || null,
      horaFin: todoElDia ? null : (horaFin || null),
      titulo: titulo.trim(),
      detalle: detalle.trim() || null,
      origen: evento?.origen ?? 'local',
      mensajeId: evento?.mensajeId ?? prefill?.mensajeId ?? null,
    };
    try {
      if (evento) await eventoApi.update(evento.id, data);
      else await eventoApi.create(data);
      setStatus('Evento guardado');
      onSaved();
      setTimeout(() => onClose(), 400);
    } catch { setStatus('Error al guardar'); }
  };

  const input = 'w-full px-2 py-1.5 text-sm rounded-lg border outline-none';
  const inputStyle = {
    backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
    borderColor: 'var(--color-border)',
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="w-[440px] rounded-xl p-5 shadow-2xl" style={{ backgroundColor: 'var(--color-bg-card)' }}>
        <h3 className="text-sm font-bold mb-4" style={{ color: 'var(--color-text)' }}>
          {evento ? 'Editar evento' : `Nuevo evento${fechaIni ? ` - ${fechaIni}` : ''}`}</h3>
        <div className="space-y-3">
          <div>
            <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Título *</label>
            <input value={titulo} onChange={e => setTitulo(e.target.value)} className={input} style={inputStyle} />
          </div>
          <div className="flex items-center gap-2">
            <input type="checkbox" id="todo-el-dia" checked={todoElDia}
              onChange={e => setTodoElDia(e.target.checked)} className="cursor-pointer" />
            <label htmlFor="todo-el-dia" className="text-xs font-bold cursor-pointer"
              style={{ color: 'var(--color-text)' }}>Todo el día</label>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Fecha *</label>
              <input type="date" value={fechaIni} onChange={e => setFechaIni(e.target.value)}
                className={input} style={inputStyle} />
            </div>
            {!todoElDia && (
              <div>
                <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Hora</label>
                <input type="time" value={hora} onChange={e => setHora(e.target.value)}
                  className={input} style={inputStyle} />
              </div>
            )}
            <div>
              <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>
                Fecha fin</label>
              <input type="date" value={fechaFin} onChange={e => setFechaFin(e.target.value)}
                className={input} style={inputStyle} />
            </div>
            {!todoElDia && (
              <div>
                <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Hora fin</label>
                <input type="time" value={horaFin} onChange={e => setHoraFin(e.target.value)}
                  className={input} style={inputStyle} />
              </div>
            )}
          </div>
          <div>
            <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Detalle</label>
            <textarea value={detalle} onChange={e => setDetalle(e.target.value)} rows={4}
              className={input + ' resize-none'} style={inputStyle} />
          </div>
          {status && (
            <p className="text-xs" style={{ color: status.includes('Error') ? '#ef4444' : '#22c55e' }}>{status}</p>
          )}
          <div className="flex gap-2 justify-end pt-2">
            <button onClick={onClose}
              className="px-3 py-1.5 text-xs rounded-lg"
              style={{ backgroundColor: 'var(--color-bg-elevated)', color: 'var(--color-text)' }}>Cancelar</button>
            <button onClick={guardar}
              className="px-3 py-1.5 text-xs font-bold rounded-pill"
              style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>Guardar</button>
          </div>
        </div>
      </div>
    </div>
  );
}
