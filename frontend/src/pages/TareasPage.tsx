import { useState, useEffect } from 'react';
import { tareaApi, eventoApi, todoistApi } from '../api/client';
import { useAsync } from '../hooks/useAsync';
import { Spinner, EmptyState, ErrorState } from '../components/StateViews';
import TareaDialog, { Tarea } from '../components/TareaDialog';
import EventoDialog, { Evento } from '../components/EventoDialog';
import { formatearFecha, etiquetaFecha } from '../utils/fechas';

// Vista de Tareas sobre la agenda unica: muestra tareas Y eventos creados en
// el calendario. Los eventos se editan con el dialogo de evento y no tienen
// checkbox; las tareas si. Dos columnas: periodos + proximos a la izquierda,
// lista estilo Planify a la derecha.
const PERIODOS = [
  { key: 'hoy', label: 'Hoy', bg: '#c62828' },
  { key: 'semana', label: 'Semana', bg: '#f9a825' },
  { key: 'mes', label: 'Mes', bg: '#2e7d32' },
  { key: 'programadas', label: 'Programadas', bg: '#5e35b1' },
];

const PRIORIDAD = { ALTA: { bg: '#ff5252', label: 'ALTA' },
                    MEDIA: { bg: '#ffca28', label: 'MEDIA' },
                    BAJA: { bg: '#69f0ae', label: 'BAJA' } } as any;

const hoyStr = () => new Date().toISOString().slice(0, 10);

export default function TareasPage() {
  const { data: items, loading, error, reload } = useAsync<Tarea[]>(
    () => tareaApi.list().then(r => r.data || []), []
  );
  const [filter, setFilter] = useState('all');
  const [tagFilter, setTagFilter] = useState<string | null>(null);
  const [titulo, setTitulo] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editando, setEditando] = useState<Tarea | null>(null);
  const [todoistOn, setTodoistOn] = useState(false);
  const [enviandoTodoist, setEnviandoTodoist] = useState<number | null>(null);
  const [aviso, setAviso] = useState('');

  // ¿Hay Todoist configurado? (la key se gestiona en Ajustes → Integraciones)
  useEffect(() => {
    todoistApi.estado()
      .then(r => setTodoistOn(!!r.data?.configurado))
      .catch(() => setTodoistOn(false));
  }, []);

  useEffect(() => {
    if (!aviso) return;
    const t = setTimeout(() => setAviso(''), 4000);
    return () => clearTimeout(t);
  }, [aviso]);

  const abrirEdicion = (item: Tarea) => { setEditando(item); setDialogOpen(true); };

  const enviarATodoist = async (t: Tarea) => {
    setEnviandoTodoist(t.id);
    try {
      await todoistApi.enviar(t.id);
      setAviso('✅ Tarea enviada a Todoist');
      reload();
    } catch (e: any) {
      setAviso('Error: ' + (e?.response?.data?.error || e?.message || 'no se pudo enviar'));
    } finally {
      setEnviandoTodoist(null);
    }
  };

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
    (items ?? []).filter(i => i.tipo === 'TAREA')
      .flatMap(t => (t.etiquetas || '').split(',').map(e => e.trim()).filter(Boolean))
  )].sort();

  const hoy = hoyStr();
  const filtered = (items ?? [])
    .filter(t => {
      if (tagFilter) {
        const tags = (t.etiquetas || '').split(',').map(e => e.trim());
        if (!tags.includes(tagFilter)) return false;
      }
      if (filter === 'all') return true;
      if (!t.fecha) return false;
      if (filter === 'hoy') return t.fecha <= hoy;   // Planify: vencidas + hoy
      if (filter === 'programadas') return true;      // con fecha, ordenadas abajo
      const d = new Date(t.fecha);
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
    // Por fecha asc (sin fecha al final), luego prioridad
    .sort((a, b) => (a.fecha || '9999').localeCompare(b.fecha || '9999'));

  // Próximos (pendientes con fecha, de hoy en adelante) agrupados por día
  const grupos: { fecha: string; items: Tarea[] }[] = [];
  for (const t of (items ?? [])
    .filter(t => t.tipo !== 'TAREA' || t.estado !== 'completada')
    .filter(t => t.fecha && t.fecha >= hoy)
    .sort((a, b) => a.fecha!.localeCompare(b.fecha!))) {
    const g = grupos[grupos.length - 1];
    if (g && g.fecha === t.fecha) g.items.push(t);
    else grupos.push({ fecha: t.fecha!, items: [t] });
  }

  return (
    <div className="flex h-full min-w-0" style={{ backgroundColor: 'var(--color-bg)' }}>
      {/* ── Columna izquierda (20%): botonera 2x2 + próximos por día ── */}
      <aside className="w-1/5 min-w-[190px] p-3 flex flex-col overflow-hidden border-r"
        style={{ borderColor: 'var(--color-border)' }}>
        <div className="grid grid-cols-2 gap-1.5 mb-3">
          {PERIODOS.map(f => (
            <button key={f.key}
              onClick={() => setFilter(filter === f.key ? 'all' : f.key)}
              className="px-2 py-2 text-xs font-bold rounded-lg transition-colors"
              style={{
                backgroundColor: filter === f.key ? f.bg : 'var(--color-bg-card)',
                color: filter === f.key ? 'white' : 'var(--color-text)',
              }}>{f.label}</button>
          ))}
        </div>

        <div className="flex-1 overflow-y-auto space-y-3">
          {grupos.length === 0 ? (
            <p className="text-xs px-1" style={{ color: 'var(--color-text-secondary)' }}>
              Sin tareas próximas</p>
          ) : grupos.map(g => (
            <div key={g.fecha}>
              <p className="text-[11px] font-bold px-1 mb-1 sticky top-0 py-0.5"
                style={{ color: 'var(--color-text-secondary)', backgroundColor: 'var(--color-bg)' }}>
                {etiquetaFecha(g.fecha)}</p>
              {g.items.map(t => (
                <div key={t.id}
                  className="flex items-center gap-1.5 px-1.5 py-1 rounded-lg cursor-pointer text-xs"
                  style={{ backgroundColor: 'var(--color-bg-card)' }}
                  title="Clic para editar"
                  onClick={() => abrirEdicion(t)}>
                  <span className="w-1.5 h-1.5 rounded-full shrink-0"
                    style={{ backgroundColor: t.tipo === 'EVENTO' ? '#a78bfa'
                      : (PRIORIDAD[t.prioridad || 'MEDIA'] || PRIORIDAD.MEDIA).bg }} />
                  <span className="truncate" style={{ color: 'var(--color-text)' }}>{t.titulo}</span>
                </div>
              ))}
            </div>
          ))}
        </div>
      </aside>

      {/* ── Derecha (80%): lista estilo Planify ── */}
      <main className="flex-1 p-4 overflow-y-auto min-w-0">
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

        {aviso && (
          <p className="text-xs mb-3"
            style={{ color: aviso.startsWith('Error') ? '#ef4444' : '#22c55e' }}>{aviso}</p>
        )}

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
              const esTarea = t.tipo === 'TAREA';
              const prio = PRIORIDAD[t.prioridad || 'MEDIA'] || PRIORIDAD.MEDIA;
              return (
                <div key={t.id}
                  className="flex items-center gap-2 px-2 py-1.5 rounded-lg text-sm"
                  style={{
                    backgroundColor: t.estado === 'completada'
                      ? 'var(--color-bg-elevated)' : 'var(--color-bg-card)',
                    opacity: t.estado === 'completada' ? 0.6 : 1,
                    borderLeft: esTarea ? 'none' : '3px solid #a78bfa',
                  }}>
                  {esTarea ? (
                    <input type="checkbox" checked={t.estado === 'completada'}
                      onChange={() => toggleEstado(t)}
                      className="cursor-pointer" />
                  ) : (
                    <span className="text-xs" title="Evento del calendario">📅</span>
                  )}
                  <span className={`flex-1 cursor-pointer ${t.estado === 'completada' ? 'line-through' : ''}`}
                    style={{ color: 'var(--color-text)' }}
                    title="Clic para editar"
                    onClick={() => abrirEdicion(t)}>{t.titulo}</span>
                  <button
                    onClick={() => abrirEdicion(t)}
                    className="text-xs px-1.5 py-0.5 rounded"
                    style={{ color: 'var(--color-text-secondary)' }}>✏️</button>
                  {todoistOn && esTarea && (
                    t.todoistId ? (
                      <span className="text-[10px] px-1.5 py-0.5 rounded font-bold"
                        style={{ backgroundColor: '#166534', color: 'white' }} title="Ya está en Todoist">
                        ☑ Todoist</span>
                    ) : (
                      <button onClick={() => enviarATodoist(t)} disabled={enviandoTodoist === t.id}
                        className="text-[10px] px-1.5 py-0.5 rounded font-bold disabled:opacity-40"
                        style={{ backgroundColor: 'var(--color-bg-elevated)', color: 'var(--color-text)' }}
                        title="Enviar esta tarea a Todoist">
                        {enviandoTodoist === t.id ? '…' : '→ Todoist'}</button>
                    )
                  )}
                  {esTarea && (
                    <span className="text-xs px-1.5 py-0.5 rounded font-bold"
                      style={{ backgroundColor: prio.bg, color: prio.bg === '#ffca28' ? '#111' : 'white' }}>
                      {prio.label}</span>
                  )}
                  {t.fecha && (
                    <span className="text-xs font-medium"
                      style={{ color: t.fecha < hoy && t.estado !== 'completada'
                        ? '#ef4444' : 'var(--color-text-secondary)' }}>
                      {t.fecha < hoy && t.estado !== 'completada' ? '⚠ ' : ''}{formatearFecha(t.fecha)}
                      {t.hora ? ` ${t.hora}` : ''}</span>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </main>

      {editando?.tipo === 'EVENTO' ? (
        <EventoDialog
          key={editando.id}
          open={dialogOpen}
          evento={editando as Evento}
          onClose={() => { setDialogOpen(false); setEditando(null); }}
          onSaved={reload} />
      ) : (
        <TareaDialog
          key={editando?.id ?? 'nueva'}
          open={dialogOpen}
          tarea={editando}
          onClose={() => { setDialogOpen(false); setEditando(null); }}
          onSaved={reload} />
      )}
    </div>
  );
}
