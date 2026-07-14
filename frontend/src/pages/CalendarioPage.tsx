import { useState, useEffect } from 'react';
import { eventoApi } from '../api/client';
import EventoDialog from '../components/EventoDialog';

const DAYS = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
const MONTHS = ['Enero','Febrero','Marzo','Abril','Mayo','Junio',
                'Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];

export default function CalendarioPage() {
  const [today] = useState(() => new Date());
  const [month, setMonth] = useState(today.getMonth());
  const [year, setYear] = useState(today.getFullYear());
  const [events, setEvents] = useState<string[]>([]);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [selectedDate, setSelectedDate] = useState('');

  useEffect(() => {
    eventoApi.datesWithEvents()
      .then(r => setEvents(r.data || []))
      .catch(() => {});
  }, [month, year]);

  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const todayStr = `${today.getFullYear()}-${String(today.getMonth()+1).padStart(2,'0')}-${String(today.getDate()).padStart(2,'0')}`;

  const prevMonth = () => { if (month === 0) { setMonth(11); setYear(y => y-1); } else setMonth(m => m-1); };
  const nextMonth = () => { if (month === 11) { setMonth(0); setYear(y => y+1); } else setMonth(m => m+1); };

  const cells: (number | null)[] = [];
  for (let i = 0; i < firstDay; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);

  const fechaStr = (d: number) => `${year}-${String(month+1).padStart(2,'0')}-${String(d).padStart(2,'0')}`;

  return (
    <div className="p-4 h-full overflow-auto" style={{ backgroundColor: 'var(--color-bg)' }}>
      <div className="flex items-center gap-4 mb-4">
        <button onClick={prevMonth} className="px-3 py-1 text-sm rounded-lg transition-colors"
          style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text)' }}>◀</button>
        <h2 className="text-lg font-bold" style={{ color: 'var(--color-text)' }}>
          {MONTHS[month]} {year}</h2>
        <button onClick={nextMonth} className="px-3 py-1 text-sm rounded-lg transition-colors"
          style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text)' }}>▶</button>
      </div>

      <div className="grid grid-cols-7 gap-px">
        {DAYS.map(d => (
          <div key={d} className="text-center text-xs font-bold py-1"
            style={{ color: 'var(--color-text-secondary)' }}>{d}</div>
        ))}
        {cells.map((d, i) => {
          const fs = d ? fechaStr(d) : '';
          const isToday = fs === todayStr;
          const hasEvents = events.includes(fs);
          return (
            <div key={i} onClick={() => { if (d) { setSelectedDate(fs); setDialogOpen(true); } }}
              className="min-h-[80px] p-1 text-sm rounded transition-colors cursor-pointer"
              style={{
                backgroundColor: d ? (isToday ? '#1a3a5c' : 'var(--color-bg-card)') : 'transparent',
                border: isToday ? '2px solid var(--color-accent)' : '1px solid var(--color-border)',
                color: d ? 'var(--color-text)' : 'transparent',
              }}>
              {d && <span className="font-bold text-xs">{d}</span>}
              {hasEvents && <div className="w-1.5 h-1.5 rounded-full mt-1"
                style={{ backgroundColor: 'var(--color-accent)' }} />}
            </div>
          );
        })}
      </div>
      {/* Dialog para crear evento */}
      <EventoDialog
        open={dialogOpen}
        fecha={selectedDate}
        onClose={() => setDialogOpen(false)}
        onSaved={() => {
          eventoApi.datesWithEvents().then(r => setEvents(r.data || [])).catch(() => {});
        }}
      />
    </div>
  );
}
