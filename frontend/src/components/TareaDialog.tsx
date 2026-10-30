import { useState } from 'react';
import { tareaApi } from '../api/client';
import type { AgendaItem } from '../types/agenda';

export type Tarea = AgendaItem;

// Diálogo para crear/editar TAREAS (desde Tareas, Calendario o un correo).
// Con fecha y hora, la tarea se pinta en el calendario a esa hora; sin fecha,
// solo se ve en Tareas.
interface TareaDialogProps {
  open: boolean;
  tarea?: Tarea | null;
  prefill?: { titulo?: string; detalle?: string; fecha?: string; hora?: string; mensajeId?: number };
  onClose: () => void;
  onSaved: () => void;
}

export default function TareaDialog({ open, tarea, prefill, onClose, onSaved }: TareaDialogProps) {
  const [titulo, setTitulo] = useState(tarea?.titulo ?? prefill?.titulo ?? '');
  const [detalle, setDetalle] = useState(tarea?.detalle ?? prefill?.detalle ?? '');
  const [fecha, setFecha] = useState(tarea?.fecha ?? prefill?.fecha ?? '');
  const [hora, setHora] = useState(tarea?.hora ?? prefill?.hora ?? '');
  const [estado, setEstado] = useState(tarea?.estado ?? 'pendiente');
  const [prioridad, setPrioridad] = useState(tarea?.prioridad ?? 'MEDIA');
  const [etiquetas, setEtiquetas] = useState(tarea?.etiquetas ?? '');
  const [status, setStatus] = useState('');

  if (!open) return null;

  const guardar = async () => {
    if (!titulo.trim()) { setStatus('El título es obligatorio'); return; }
    const data = {
      titulo: titulo.trim(),
      detalle: detalle.trim() || null,
      fecha: fecha || null,
      hora: hora || null,
      todoElDia: !hora,
      estado,
      etiquetas: etiquetas.trim() || null,
      prioridad,
      mensajeId: tarea?.mensajeId ?? prefill?.mensajeId ?? null,
    };
    try {
      if (tarea) await tareaApi.update(tarea.id, data);
      else await tareaApi.create(data);
      setStatus('Tarea guardada');
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
          {tarea ? 'Editar tarea' : 'Nueva tarea'}</h3>
        <div className="space-y-3">
          <div>
            <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Título *</label>
            <input value={titulo} onChange={e => setTitulo(e.target.value)} className={input} style={inputStyle} />
          </div>
          <div>
            <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Descripción</label>
            <textarea value={detalle} onChange={e => setDetalle(e.target.value)} rows={3}
              className={input + ' resize-none'} style={inputStyle} />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Fecha</label>
              <input type="date" value={fecha || ''} onChange={e => setFecha(e.target.value)}
                className={input} style={inputStyle} />
            </div>
            <div>
              <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Hora</label>
              <input type="time" value={hora || ''} onChange={e => setHora(e.target.value)}
                className={input} style={inputStyle} />
            </div>
            <div>
              <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Prioridad</label>
              <select value={prioridad} onChange={e => setPrioridad(e.target.value)}
                className={input} style={inputStyle}>
                <option value="ALTA">ALTA</option>
                <option value="MEDIA">MEDIA</option>
                <option value="BAJA">BAJA</option>
              </select>
            </div>
            {tarea && (
              <div>
                <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Estado</label>
                <select value={estado} onChange={e => setEstado(e.target.value)}
                  className={input} style={inputStyle}>
                  <option value="pendiente">Pendiente</option>
                  <option value="en_progreso">En progreso</option>
                  <option value="completada">Completada</option>
                </select>
              </div>
            )}
          </div>
          <div>
            <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Etiquetas</label>
            <input value={etiquetas} onChange={e => setEtiquetas(e.target.value)} placeholder="casa, trabajo..."
              className={input} style={inputStyle} />
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
