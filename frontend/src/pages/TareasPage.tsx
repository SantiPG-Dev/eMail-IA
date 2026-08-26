import { useState } from 'react';
import { tareaApi } from '../api/client';
import { useAsync } from '../hooks/useAsync';
import { Spinner, EmptyState, ErrorState } from '../components/StateViews';
import TareaDialog, { Tarea } from '../components/TareaDialog';

// Gestión de tareas con vistas estilo Planify (Hoy con vencidas incluidas,
// Programadas, etiquetas como filtros clicables) más periodo semana/mes.
const FILTROS = [
  { key: 'all', label: 'Todas', bg: 'var(--color-bg-card)' },
  { key: 'hoy', label: 'Hoy', bg: '#c62828' },
  { key: 'programadas', label: 'Programadas', bg: '#5e35b1' },
  { key: 'semana', label: 'Semana', bg: '#f9a825' },
  { key: 'mes', label: 'Mes', bg: '#2e7d32' },
];

const PRIORIDAD = { ALTA: { bg: '#ff5252', label: 'ALTA' },
                    MEDIA: { bg: '#ffca28', label: 'MEDIA' },
                    BAJA: { bg: '#69f0ae', label: 'BAJA' } } as any;

const hoyStr = () => new Date().toISOString().slice(0, 10);

export default function TareasPage() {
  const { data: tareas, loading, error, reload } = useAsync<Tarea[]>(
    () => tareaApi.list().then(r => r.data || []), []
  );
  const [filter, setFilter] = useState('all');
  const [tagFilter, setTagFilter] = useState<string | null>(null);
  const [titulo, setTitulo] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editando, setEditando] = useState<Tarea | null>(null);

  const add = async () => {
    if (!titulo.trim()) return;
    await tareaApi.create({ titulo, prioridad: 'MEDIA', estado: 'pendiente' });
    setTitulo('');
    reload();
  };

  const toggleEstado = async (t: Tarea) => {
    const nuevoEstado = t.estado === 'completada' ? 'pendiente' : 'completada';
    await tareaApi.update(t.id, { ...t, estado: nuevoEstado });
    reload();
  };

  // Etiquetas únicas de todas las tareas ("casa, trabajo" → chips clicables)
  const etiquetas = [...new Set(
    (tareas ?? []).flatMap(t => (t.etiquetas || '').split(',').map(e => e.trim()).filter(Boolean))
  )].sort();

  const hoy = hoyStr();
  const filtered = (tareas ?? [])
    .filter(t => {
      if (tagFilter) {
        const tags = (t.etiquetas || '').split(',').map(e => e.trim());
        if (!tags.includes(tagFilter)) return false;
      }
      if (filter === 'all') return true;
      if (!t.fechaVencimiento) return false;
      if (filter === 'hoy') return t.fechaVencimiento <= hoy;   // Planify: vencidas + hoy
      if (filter === 'programadas') return true;                 // con fecha, ordenadas abajo
      const d = new Date(t.fechaVencimiento);
      const now = new Date();
      if (filter === 'semana') {
        const weekEnd = new Date(now); weekEnd.setDate(now.getDate() + 7);
        return d >= now && d <= weekEnd;
      }
      if (filter === 'mes') {
        return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
      }
      return true;
    })
    // Por vencimiento asc (sin fecha al final), luego prioridad
    .sort((a, b) => (a.fechaVencimiento || '9999').localeCompare(b.fechaVencimiento || '9999'));

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

      {/* Etiquetas clicables (estilo Planify) */}
      {etiquetas.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-4">
          {etiquetas.map(tag => (
            <button key={tag} onClick={() => setTagFilter(tagFilter === tag ? null : tag)}
              className="px-2 py-0.5 text-[11px] rounded-full transition-colors"
              style={{
                backgroundColor: tagFilter === tag ? 'var(--color-accent)' : 'var(--color-bg-card)',
                color: tagFilter === tag ? '#0F172A' : 'var(--color-text-secondary)',
                border: '1px solid var(--color-border)',
              }}>#{tag}</button>
          ))}
        </div>
      )}

      {/* Lista */}
      {loading ? (
        <Spinner label="Cargando tareas..." />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : filtered.length === 0 ? (
        <EmptyState icon="📝" title="No hay tareas" hint="Añade una arriba para empezar" />
      ) : (
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
                <span className={`flex-1 cursor-pointer ${t.estado === 'completada' ? 'line-through' : ''}`}
                  style={{ color: 'var(--color-text)' }}
                  title="Clic para editar"
                  onClick={() => { setEditando(t); setDialogOpen(true); }}>{t.titulo}</span>
                <button
                  onClick={() => { setEditando(t); setDialogOpen(true); }}
                  className="text-xs px-1.5 py-0.5 rounded"
                  style={{ color: 'var(--color-text-secondary)' }}>✏️</button>
                <span className="text-xs px-1.5 py-0.5 rounded font-bold"
                  style={{ backgroundColor: prio.bg, color: prio.bg === '#ffca28' ? '#111' : 'white' }}>
                  {prio.label}</span>
                {t.fechaVencimiento && (
                  <span className="text-xs font-medium"
                    style={{ color: t.fechaVencimiento < hoy && t.estado !== 'completada'
                      ? '#ef4444' : 'var(--color-text-secondary)' }}>
                    {t.fechaVencimiento < hoy && t.estado !== 'completada' ? '⚠ ' : ''}{t.fechaVencimiento}</span>
                )}
              </div>
            );
          })}
        </div>
      )}

      <TareaDialog
        key={editando?.id ?? 'nueva'}
        open={dialogOpen}
        tarea={editando}
        onClose={() => { setDialogOpen(false); setEditando(null); }}
        onSaved={reload} />
    </div>
  );
}
