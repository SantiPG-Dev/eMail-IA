import { useState } from 'react';
import { eventoApi } from '../api/client';
import EventoDialog, { Evento } from '../components/EventoDialog';
import { useAsync } from '../hooks/useAsync';
import { Spinner, ErrorState, EmptyState } from '../components/StateViews';

const DAYS = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
const MONTHS = ['Enero','Febrero','Marzo','Abril','Mayo','Junio',
                'Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];

// Calendario mensual + panel lateral del día seleccionado (estilo Evolution):
// lista de eventos del día, crear/editar/borrar. Los días con eventos se marcan.
export default function CalendarioPage() {
  const [today] = useState(() => new Date());
  const [month, setMonth] = useState(today.getMonth());
  const [year, setYear] = useState(today.getFullYear());
  const { data: events, loading, error, reload } = useAsync<string[]>(
    () => eventoApi.datesWithEvents().then(r => r.data || []), [month, year]
  );

  // Día seleccionado → panel lateral con sus eventos
  const [selectedDate, setSelectedDate] = useState('');
  const { data: eventosDia, loading: loadingDia, reload: reloadDia } = useAsync<Evento[]>(
    () => selectedDate ? eventoApi.listByDate(selectedDate).then(r => r.data || []) : Promise.resolve([]),
    [selectedDate]
  );

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editando, setEditando] = useState<Evento | null>(null);

  const eventList = events ?? [];

  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const todayStr = `${today.getFullYear()}-${String(today.getMonth()+1).padStart(2,'0')}-${String(today.getDate()).padStart(2,'0')}`;

  const prevMonth = () => { if (month === 0) { setMonth(11); setYear(y => y-1); } else setMonth(m => m-1); };
  const nextMonth = () => { if (month === 11) { setMonth(0); setYear(y => y+1); } else setMonth(m => m+1); };

  const cells: (number | null)[] = [];
  for (let i = 0; i < firstDay; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);

  const fechaStr = (d: number) => `${year}-${String(month+1).padStart(2,'0')}-${String(d).padStart(2,'0')}`;

  const trasGuardar = () => { reload(); reloadDia(); };

  const borrarEvento = async (id: number) => {
    if (!window.confirm('¿Borrar el evento?')) return;
    await eventoApi.delete(id);
    trasGuardar();
  };

  return (
    <div className="p-4 h-full overflow-auto flex gap-4" style={{ backgroundColor: 'var(--color-bg)' }}>
      {/* Mes */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-4 mb-4">
          <button onClick={prevMonth} className="px-3 py-1 text-sm rounded-lg transition-colors"
            style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text)' }}>◀</button>
          <h2 className="text-lg font-bold" style={{ color: 'var(--color-text)' }}>
            {MONTHS[month]} {year}</h2>
          <button onClick={nextMonth} className="px-3 py-1 text-sm rounded-lg transition-colors"
            style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text)' }}>▶</button>
          <div className="flex-1" />
          <button onClick={() => { setEditando(null); setDialogOpen(true); }}
            className="px-3 py-1 text-xs font-bold rounded-pill"
            style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>+ Nuevo evento</button>
        </div>

        {loading ? (
          <Spinner label="Cargando calendario..." />
        ) : error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : (
          <div className="grid grid-cols-7 gap-px">
            {DAYS.map(d => (
              <div key={d} className="text-center text-xs font-bold py-1"
                style={{ color: 'var(--color-text-secondary)' }}>{d}</div>
            ))}
            {cells.map((d, i) => {
              const fs = d ? fechaStr(d) : '';
              const isToday = fs === todayStr;
              const hasEvents = eventList.includes(fs);
              const isSelected = fs === selectedDate;
              return (
                <div key={i} onClick={() => { if (d) setSelectedDate(fs); }}
                  className="min-h-[80px] p-1 text-sm rounded transition-colors cursor-pointer"
                  style={{
                    backgroundColor: d ? (isToday ? '#1a3a5c' : 'var(--color-bg-card)') : 'transparent',
                    border: isSelected ? '2px solid var(--color-accent)'
                      : isToday ? '2px solid var(--color-accent)' : '1px solid var(--color-border)',
                    color: d ? 'var(--color-text)' : 'transparent',
                  }}>
                  {d && <span className="font-bold text-xs">{d}</span>}
                  {hasEvents && <div className="w-1.5 h-1.5 rounded-full mt-1"
                    style={{ backgroundColor: 'var(--color-accent)' }} />}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Panel del día */}
      {selectedDate && (
        <div className="w-[300px] shrink-0 rounded-xl p-3 flex flex-col gap-2 self-start max-h-full"
          style={{ backgroundColor: 'var(--color-bg-card)' }}>
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-bold flex-1" style={{ color: 'var(--color-text)' }}>
              {selectedDate === todayStr ? 'Hoy' : ''}
            </h3>
            <button onClick={() => setSelectedDate('')}
              className="text-xs px-1.5 rounded"
              style={{ color: 'var(--color-text-secondary)' }}>✕</button>
          </div>
          <p className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>{selectedDate}</p>

          {loadingDia ? <Spinner label="..." /> : (eventosDia ?? []).length === 0 ? (
            <EmptyState icon="📆" title="Sin eventos" hint="Haz clic en '+ Nuevo evento'" />
          ) : (
            <div className="space-y-1 overflow-y-auto">
              {(eventosDia ?? []).map(ev => (
                <div key={ev.id}
                  className="rounded-lg px-2 py-1.5 text-xs cursor-pointer hover:brightness-125"
                  style={{ backgroundColor: 'var(--color-bg-elevated)' }}
                  onClick={() => { setEditando(ev); setDialogOpen(true); }}>
                  <div className="flex items-center gap-2">
                    <span className="font-bold shrink-0"
                      style={{ color: 'var(--color-accent)' }}>
                      {ev.todoElDia ? 'Todo el día' : (ev.hora || '--:--')}
                    </span>
                    <span className="flex-1 truncate" style={{ color: 'var(--color-text)' }}>{ev.titulo}</span>
                  </div>
                  {(ev.horaFin || ev.fechaFin) && !ev.todoElDia && (
                    <p className="opacity-60" style={{ color: 'var(--color-text)' }}>
                      hasta {ev.horaFin || ''}{ev.fechaFin && ev.fechaFin !== ev.fecha ? ` ${ev.fechaFin}` : ''}
                    </p>
                  )}
                  {ev.origen === 'ics' && (
                    <p className="text-[10px] opacity-50" style={{ color: 'var(--color-text)' }}>importado (ics)</p>
                  )}
                  <button
                    onClick={e => { e.stopPropagation(); borrarEvento(ev.id); }}
                    className="text-[10px] mt-1 px-1.5 py-0.5 rounded"
                    style={{ backgroundColor: '#ef4444', color: 'white' }}>Borrar</button>
                </div>
              ))}
            </div>
          )}
          <button onClick={() => { setEditando(null); setDialogOpen(true); }}
            className="px-3 py-1.5 text-xs font-bold rounded-pill"
            style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>+ Añadir en este día</button>
        </div>
      )}

      {/* Diálogo crear/editar evento */}
      <EventoDialog
        key={editando?.id ?? 'nuevo'}
        open={dialogOpen}
        fecha={selectedDate || undefined}
        evento={editando}
        onClose={() => { setDialogOpen(false); setEditando(null); }}
        onSaved={trasGuardar} />
    </div>
  );
}
