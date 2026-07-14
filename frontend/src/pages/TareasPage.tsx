import { useState, useEffect } from 'react';
import { tareaApi } from '../api/client';

interface Tarea {
  id: number; titulo: string; descripcion: string;
  fechaVencimiento: string; estado: string; prioridad: string;
}

const FILTROS = [
  { key: 'all', label: 'Todas', bg: 'var(--color-bg-card)' },
  { key: 'hoy', label: 'Hoy', bg: '#c62828' },
  { key: 'semana', label: 'Semana', bg: '#f9a825' },
  { key: 'mes', label: 'Mes', bg: '#2e7d32' },
];

const PRIORIDAD = { ALTA: { bg: '#ff5252', label: 'ALTA' },
                    MEDIA: { bg: '#ffca28', label: 'MEDIA' },
                    BAJA: { bg: '#69f0ae', label: 'BAJA' } } as any;

export default function TareasPage() {
  const [tareas, setTareas] = useState<Tarea[]>([]);
  const [filter, setFilter] = useState('all');
  const [titulo, setTitulo] = useState('');

  useEffect(() => { tareaApi.list().then(r => setTareas(r.data)).catch(() => {}); }, []);

  const add = async () => {
    if (!titulo.trim()) return;
    await tareaApi.create({ titulo, prioridad: 'MEDIA', estado: 'pendiente' });
    setTitulo('');
    tareaApi.list().then(r => setTareas(r.data));
  };

  const toggleEstado = async (t: Tarea) => {
    const nuevoEstado = t.estado === 'completada' ? 'pendiente' : 'completada';
    await tareaApi.update(t.id, { ...t, estado: nuevoEstado });
    tareaApi.list().then(r => setTareas(r.data));
  };

  const filtered = tareas.filter(t => {
    if (filter === 'all') return true;
    if (!t.fechaVencimiento) return false;
    const d = new Date(t.fechaVencimiento);
    const now = new Date();
    if (filter === 'hoy') return d.toDateString() === now.toDateString();
    if (filter === 'semana') {
      const weekEnd = new Date(now); weekEnd.setDate(now.getDate() + 7);
      return d >= now && d <= weekEnd;
    }
    if (filter === 'mes') {
      return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
    }
    return true;
  });

  return (
    <div className="p-4 h-full overflow-auto" style={{ backgroundColor: 'var(--color-bg)' }}>
      {/* Input nueva tarea */}
      <div className="flex gap-2 mb-4">
        <input value={titulo} onChange={e => setTitulo(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && add()}
          placeholder="Nueva tarea..."
          className="flex-1 px-2 py-1.5 text-sm rounded-lg border outline-none"
          style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                   borderColor: 'var(--color-border)' }} />
        <button onClick={add}
          className="px-4 py-1.5 text-sm font-bold rounded-pill"
          style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>Añadir</button>
      </div>

      {/* Filtros */}
      <div className="flex gap-2 mb-4">
        {FILTROS.map(f => (
          <button key={f.key} onClick={() => setFilter(f.key)}
            className="px-3 py-1 text-xs font-bold rounded-lg transition-colors"
            style={{
              backgroundColor: filter === f.key ? f.bg : 'var(--color-bg-card)',
              color: filter === f.key && f.key !== 'all' ? 'white' : 'var(--color-text)',
            }}>{f.label}</button>
        ))}
      </div>

      {/* Lista */}
      <div className="space-y-1">
        {filtered.map(t => {
          const prio = PRIORIDAD[t.prioridad] || PRIORIDAD.MEDIA;
          return (
            <div key={t.id}
              className="flex items-center gap-2 px-2 py-1.5 rounded-lg text-sm"
              style={{
                backgroundColor: t.estado === 'completada'
                  ? 'var(--color-bg-elevated)' : 'var(--color-bg-card)',
                opacity: t.estado === 'completada' ? 0.6 : 1,
              }}>
              <input type="checkbox" checked={t.estado === 'completada'}
                onChange={() => toggleEstado(t)}
                className="cursor-pointer" />
              <span className={`flex-1 ${t.estado === 'completada' ? 'line-through' : ''}`}
                style={{ color: 'var(--color-text)' }}>{t.titulo}</span>
              <span className="text-xs px-1.5 py-0.5 rounded font-bold"
                style={{ backgroundColor: prio.bg, color: prio.bg === '#ffca28' ? '#111' : 'white' }}>
                {prio.label}</span>
              {t.fechaVencimiento && (
                <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
                  {t.fechaVencimiento}</span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
