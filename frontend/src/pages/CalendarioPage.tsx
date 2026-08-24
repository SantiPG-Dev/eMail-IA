import { useState } from 'react';
import { eventoApi } from '../api/client';
import EventoDialog, { Evento } from '../components/EventoDialog';
import { useAsync } from '../hooks/useAsync';
import { Spinner, ErrorState, EmptyState } from '../components/StateViews';
import { proximosEventos, diaAgenda, etiquetaFecha, compararEventos, toIso } from '../utils/fechas';

const DAYS = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
const MONTHS = ['Enero','Febrero','Marzo','Abril','Mayo','Junio',
                'Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];

const VISTAS = [
  { key: 'mes', label: 'Mes' },
  { key: 'semana', label: 'Semana' },
  { key: 'dia', label: 'Día' },
  { key: 'agenda', label: 'Agenda' },
] as const;
type Vista = typeof VISTAS[number]['key'];

const addDays = (fechaIso: string, n: number) => {
  const d = new Date(fechaIso + 'T12:00:00');
  d.setDate(d.getDate() + n);
  return toIso(d);
};

// Lunes de la semana de `fechaIso`
const mondayOf = (fechaIso: string) => {
  const d = new Date(fechaIso + 'T12:00:00');
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return toIso(d);
};

// Eventos vigentes un día concreto (incluye multi-día que lo abarcan)
const eventosDelDia = (evs: Evento[], dia: string) =>
  evs.filter(e => e.fecha <= dia && dia <= (e.fechaFin || e.fecha)).sort(compararEventos);

// Fila de evento reutilizada por el panel del día y la vista agenda.
function EventoRow({ ev, onEdit, onDelete }: { ev: Evento; onEdit: () => void; onDelete: () => void }) {
  return (
    <div
      className="rounded-lg px-2 py-1.5 text-xs cursor-pointer hover:brightness-125"
      style={{ backgroundColor: 'var(--color-bg-elevated)' }}
      onClick={onEdit}>
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
        onClick={e => { e.stopPropagation(); onDelete(); }}
        className="text-[10px] mt-1 px-1.5 py-0.5 rounded"
        style={{ backgroundColor: '#ef4444', color: 'white' }}>Borrar</button>
    </div>
  );
}

// Calendario con vistas Mes/Semana/Día/Agenda (estilo Evolution) y panel
// lateral del día seleccionado en vista Mes. Los días con eventos se marcan.
export default function CalendarioPage() {
  const [today] = useState(() => new Date());
  const [month, setMonth] = useState(today.getMonth());
  const [year, setYear] = useState(today.getFullYear());
  const [vista, setVista] = useState<Vista>('mes');
  // Fecha de referencia para las vistas Semana/Día
  const [refDate, setRefDate] = useState(() => toIso(new Date()));
  const { data: events, loading, error, reload } = useAsync<string[]>(
    () => eventoApi.datesWithEvents().then(r => r.data || []), [month, year]
  );
  // Lista completa para semana/día/agenda (se recarga al navegar)
  const { data: todos, loading: loadingTodos, error: errorTodos, reload: reloadTodos } = useAsync<Evento[]>(
    () => vista === 'mes' ? Promise.resolve([]) : eventoApi.list().then(r => r.data || []),
    [vista, refDate]
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

  const trasGuardar = () => { reload(); reloadDia(); reloadTodos(); };

  const borrarEvento = async (id: number) => {
    if (!window.confirm('¿Borrar el evento?')) return;
    await eventoApi.delete(id);
    trasGuardar();
  };

  // Agrupa la agenda plana por día (multi-día vigentes cuelgan de Hoy)
  const agendaGrupos = proximosEventos(todos ?? [], todayStr).reduce<{ dia: string; evs: Evento[] }[]>((acc, ev) => {
    const dia = diaAgenda(ev, todayStr);
    const last = acc[acc.length - 1];
    if (last && last.dia === dia) last.evs.push(ev);
    else acc.push({ dia, evs: [ev] });
    return acc;
  }, []);

  const weekStart = mondayOf(refDate);
  const weekDays = Array.from({ length: 7 }, (_, i) => addDays(weekStart, i));

  return (
    <div className="p-4 h-full overflow-auto flex gap-4" style={{ backgroundColor: 'var(--color-bg)' }}>
      {/* Mes */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-4 mb-4">
          {vista === 'mes' && (
            <>
              <button onClick={prevMonth} className="px-3 py-1 text-sm rounded-lg transition-colors"
                style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text)' }}>◀</button>
              <h2 className="text-lg font-bold" style={{ color: 'var(--color-text)' }}>
                {MONTHS[month]} {year}</h2>
              <button onClick={nextMonth} className="px-3 py-1 text-sm rounded-lg transition-colors"
                style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text)' }}>▶</button>
            </>
          )}
          {vista === 'semana' && (
            <>
              <button onClick={() => setRefDate(addDays(weekStart, -7))} className="px-3 py-1 text-sm rounded-lg transition-colors"
                style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text)' }}>◀</button>
              <h2 className="text-lg font-bold whitespace-nowrap" style={{ color: 'var(--color-text)' }}>
                {(() => {
                  const mIni = new Date(weekStart + 'T12:00:00').getMonth();
                  const mFin = new Date(weekDays[6] + 'T12:00:00').getMonth();
                  return mIni === mFin
                    ? `${weekStart.slice(8)} – ${weekDays[6].slice(8)} de ${MONTHS[mFin]}`
                    : `${weekStart.slice(8)} de ${MONTHS[mIni]} – ${weekDays[6].slice(8)} de ${MONTHS[mFin]}`;
                })()}
              </h2>
              <button onClick={() => setRefDate(addDays(weekStart, 7))} className="px-3 py-1 text-sm rounded-lg transition-colors"
                style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text)' }}>▶</button>
            </>
          )}
          {vista === 'dia' && (
            <>
              <button onClick={() => setRefDate(addDays(refDate, -1))} className="px-3 py-1 text-sm rounded-lg transition-colors"
                style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text)' }}>◀</button>
              <h2 className="text-lg font-bold" style={{ color: 'var(--color-text)' }}>
                {etiquetaFecha(refDate, today)}</h2>
              <button onClick={() => setRefDate(addDays(refDate, 1))} className="px-3 py-1 text-sm rounded-lg transition-colors"
                style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text)' }}>▶</button>
              <button onClick={() => setRefDate(todayStr)} className="px-3 py-1 text-xs rounded-lg"
                style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text-secondary)' }}>Hoy</button>
            </>
          )}
          {vista === 'agenda' && (
            <h2 className="text-lg font-bold" style={{ color: 'var(--color-text)' }}>Próximos eventos</h2>
          )}
          <div className="flex gap-1">
            {VISTAS.map(v => (
              <button key={v.key} onClick={() => setVista(v.key)}
                className="px-3 py-1 text-xs font-bold rounded-lg transition-colors"
                style={{
                  backgroundColor: vista === v.key ? 'var(--color-accent)' : 'var(--color-bg-card)',
                  color: vista === v.key ? '#0F172A' : 'var(--color-text)',
                }}>{v.label}</button>
            ))}
          </div>
          <div className="flex-1" />
          <button onClick={() => { setEditando(null); setDialogOpen(true); }}
            className="px-3 py-1 text-xs font-bold rounded-pill"
            style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>+ Nuevo evento</button>
        </div>

        {vista === 'agenda' ? (
          loadingTodos ? (
            <Spinner label="Cargando agenda..." />
          ) : errorTodos ? (
            <ErrorState message={errorTodos} onRetry={reloadTodos} />
          ) : agendaGrupos.length === 0 ? (
            <EmptyState icon="📆" title="Sin eventos próximos" hint="Haz clic en '+ Nuevo evento'" />
          ) : (
            <div className="space-y-4 max-w-2xl">
              {agendaGrupos.map(g => (
                <div key={g.dia}>
                  <p className="text-xs font-bold uppercase tracking-wide mb-1"
                    style={{ color: 'var(--color-text-secondary)' }}>
                    {etiquetaFecha(g.dia, today)}
                  </p>
                  <div className="space-y-1">
                    {g.evs.map(ev => (
                      <EventoRow key={ev.id} ev={ev}
                        onEdit={() => { setEditando(ev); setDialogOpen(true); }}
                        onDelete={() => borrarEvento(ev.id)} />
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )
        ) : vista === 'semana' ? (
          loadingTodos ? (
            <Spinner label="Cargando semana..." />
          ) : errorTodos ? (
            <ErrorState message={errorTodos} onRetry={reloadTodos} />
          ) : (
            <div className="grid grid-cols-7 gap-2">
              {weekDays.map(dia => {
                const evs = eventosDelDia(todos ?? [], dia);
                const esHoy = dia === todayStr;
                return (
                  <div key={dia} className="flex flex-col gap-1.5 min-w-0">
                    <button onClick={() => { setRefDate(dia); setVista('dia'); }}
                      className="text-center rounded-lg py-1 cursor-pointer transition-colors"
                      style={{
                        backgroundColor: esHoy ? '#1a3a5c' : 'var(--color-bg-card)',
                        color: 'var(--color-text)',
                        outline: esHoy ? '2px solid var(--color-accent)' : 'none',
                      }}>
                      <span className="text-[10px] font-bold uppercase block"
                        style={{ color: 'var(--color-text-secondary)' }}>
                        {DAYS[new Date(dia + 'T12:00:00').getDay()]}
                      </span>
                      <span className="text-sm font-bold">{Number(dia.slice(8))}</span>
                    </button>
                    <div className="flex flex-col gap-1 min-h-[60px]">
                      {evs.map(ev => (
                        <button key={ev.id}
                          onClick={() => { setEditando(ev); setDialogOpen(true); }}
                          className="text-left rounded-lg px-1.5 py-1 text-[11px] leading-tight truncate"
                          style={{
                            backgroundColor: 'var(--color-bg-card)',
                            borderLeft: '3px solid ' + (ev.origen === 'ics' ? '#a78bfa' : 'var(--color-accent)'),
                            color: 'var(--color-text)',
                          }}
                          title={`${ev.todoElDia ? 'Todo el día' : (ev.hora || '--:--')} — ${ev.titulo}`}>
                          <span className="font-bold" style={{ color: 'var(--color-accent)' }}>
                            {ev.todoElDia ? '' : (ev.hora ? ev.hora + ' ' : '')}
                          </span>
                          {ev.titulo}
                        </button>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          )
        ) : vista === 'dia' ? (
          loadingTodos ? (
            <Spinner label="Cargando día..." />
          ) : errorTodos ? (
            <ErrorState message={errorTodos} onRetry={reloadTodos} />
          ) : (
            <div className="max-w-xl space-y-1">
              {eventosDelDia(todos ?? [], refDate).length === 0 ? (
                <EmptyState icon="📆" title="Sin eventos" hint="Haz clic en '+ Nuevo evento'" />
              ) : (
                eventosDelDia(todos ?? [], refDate).map(ev => (
                  <EventoRow key={ev.id} ev={ev}
                    onEdit={() => { setEditando(ev); setDialogOpen(true); }}
                    onDelete={() => borrarEvento(ev.id)} />
                ))
              )}
              <button onClick={() => { setEditando(null); setDialogOpen(true); }}
                className="w-full px-3 py-1.5 text-xs font-bold rounded-pill"
                style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>+ Añadir en este día</button>
            </div>
          )
        ) : loading ? (
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

      {/* Panel del día (solo en vista Mes) */}
      {selectedDate && vista === 'mes' && (
        <div className="w-[300px] shrink-0 rounded-xl p-3 flex flex-col gap-2 self-start max-h-full"
          style={{ backgroundColor: 'var(--color-bg-card)' }}>
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-bold flex-1" style={{ color: 'var(--color-text)' }}>
              {selectedDate === todayStr ? 'Hoy' : ''}
            </h3>
            <button onClick={() => { setRefDate(selectedDate); setVista('dia'); }}
              className="text-xs px-1.5 rounded"
              style={{ color: 'var(--color-accent)' }}>Ver día</button>
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
                <EventoRow key={ev.id} ev={ev}
                  onEdit={() => { setEditando(ev); setDialogOpen(true); }}
                  onDelete={() => borrarEvento(ev.id)} />
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
        fecha={vista === 'dia' ? refDate : selectedDate || undefined}
        evento={editando}
        onClose={() => { setDialogOpen(false); setEditando(null); }}
        onSaved={trasGuardar} />
    </div>
  );
}
