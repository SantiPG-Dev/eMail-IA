import { useState } from 'react';
import { eventoApi } from '../api/client';

// Diálogo para crear/editar eventos del calendario.
interface EventoDialogProps {
  open: boolean;
  fecha?: string;
  onClose: () => void;
  onSaved: () => void;
}

export default function EventoDialog({ open, fecha, onClose, onSaved }: EventoDialogProps) {
  const [titulo, setTitulo] = useState('');
  const [detalle, setDetalle] = useState('');
  const [hora, setHora] = useState('');
  const [status, setStatus] = useState('');

  if (!open) return null;

  const guardar = async () => {
    if (!titulo.trim()) { setStatus('El título es obligatorio'); return; }
    try {
      await eventoApi.create({
        fecha: fecha || new Date().toISOString().slice(0, 10),
        hora: hora || null,
        titulo: titulo.trim(),
        detalle: detalle.trim() || null,
        origen: 'local',
      });
      setStatus('Evento guardado');
      setTitulo(''); setDetalle(''); setHora('');
      onSaved();
      setTimeout(() => onClose(), 500);
    } catch { setStatus('Error al guardar'); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="w-[420px] rounded-xl p-5 shadow-2xl" style={{ backgroundColor: 'var(--color-bg-card)' }}>
        <h3 className="text-sm font-bold mb-4" style={{ color: 'var(--color-text)' }}>
          {fecha ? `Nuevo evento - ${fecha}` : 'Nuevo evento'}</h3>
        <div className="space-y-3">
          <div>
            <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Título *</label>
            <input value={titulo} onChange={e => setTitulo(e.target.value)}
              className="w-full px-2 py-1.5 text-sm rounded-lg border outline-none"
              style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)', borderColor: 'var(--color-border)' }} />
          </div>
          <div>
            <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Hora</label>
            <input value={hora} onChange={e => setHora(e.target.value)} placeholder="HH:mm"
              className="w-full px-2 py-1.5 text-sm rounded-lg border outline-none"
              style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)', borderColor: 'var(--color-border)' }} />
          </div>
          <div>
            <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Detalle</label>
            <textarea value={detalle} onChange={e => setDetalle(e.target.value)} rows={4}
              className="w-full px-2 py-1.5 text-sm rounded-lg border outline-none resize-none"
              style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)', borderColor: 'var(--color-border)' }} />
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
