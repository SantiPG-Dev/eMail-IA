import { useState, useEffect } from 'react';
import { mensajeApi } from '../api/client';

interface Mensaje {
  id: number; uid: string; remitente: string; asunto: string;
  cuerpo: string; html: string; categoria: string; prioridad: string;
  fechaRecepcion: string;
}

export default function CorreoPage() {
  const [mensajes, setMensajes] = useState<Mensaje[]>([]);
  const [selected, setSelected] = useState<Mensaje | null>(null);
  const [search, setSearch] = useState('');

  useEffect(() => {
    mensajeApi.list('local').then(r => setMensajes(r.data.mensajes || [])).catch(() => {});
  }, []);

  const handleSearch = () => {
    if (!search.trim()) return;
    mensajeApi.search('local', search)
      .then(r => setMensajes(r.data.mensajes || []))
      .catch(() => {});
  };

  const categoriaStyle = (cat: string) => {
    switch (cat) {
      case 'SPAM': return { borderLeftColor: '#ef4444', background: '#2d1619' };
      case 'PHISHING': return { borderLeftColor: '#ef4444', background: '#2d1619' };
      case 'LEGITIMO': return { borderLeftColor: '#22c55e', background: '#13281b' };
      default: return { borderLeftColor: '#fbbf24', background: '#2b2412' };
    }
  };

  return (
    <div className="flex h-full">
      {/* Panel izquierdo: lista de mensajes */}
      <div className="w-[340px] min-w-[260px] flex flex-col gap-2 p-2.5"
        style={{ backgroundColor: 'var(--color-bg)' }}>
        <div className="flex gap-1.5">
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSearch()}
            placeholder="Buscar en bandeja..."
            className="flex-1 px-2 py-1.5 text-sm rounded-lg border outline-none"
            style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                     borderColor: 'var(--color-border)' }}
          />
          <button onClick={handleSearch}
            className="px-3 py-1.5 text-xs font-bold rounded-pill"
            style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>Buscar</button>
        </div>

        <div className="flex-1 overflow-y-auto space-y-1">
          {mensajes.map(m => (
            <div key={m.id} onClick={() => setSelected(m)}
              className="px-2.5 py-1.5 rounded-lg cursor-pointer text-sm"
              style={{
                ...categoriaStyle(m.categoria),
                borderLeft: '4px solid',
                ...(selected?.id === m.id ? { backgroundColor: 'var(--color-accent-selected)', color: '#0F172A' } : {}),
              }}>
              <div className="font-bold text-xs">{m.remitente || '(sin remitente)'}</div>
              <div className="text-xs truncate mt-0.5">{m.asunto}</div>
            </div>
          ))}
          {mensajes.length === 0 && (
            <p className="text-xs text-center mt-4" style={{ color: 'var(--color-text-secondary)' }}>
              No hay mensajes. Sincroniza una cuenta.
            </p>
          )}
        </div>
      </div>

      {/* Panel derecho: detalle del mensaje */}
      <div className="flex-1 flex flex-col p-2.5 gap-2 overflow-hidden"
        style={{ backgroundColor: 'var(--color-bg)' }}>
        {selected ? (
          <>
            <h3 className="text-base font-bold"
              style={{ color: 'var(--color-accent-selected)' }}>{selected.asunto}</h3>
            <p className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
              {selected.remitente} · {selected.fechaRecepcion?.slice(0, 10)}
            </p>

            {/* Cuerpo del mensaje */}
            <div className="flex-1 overflow-auto rounded-lg p-2"
              style={{ backgroundColor: 'var(--color-bg-card)' }}>
              {selected.html ? (
                <iframe
                  srcDoc={selected.html}
                  className="w-full h-full border-0"
                  title="Cuerpo del correo"
                  sandbox="allow-same-origin"
                />
              ) : (
                <pre className="text-sm whitespace-pre-wrap font-sans"
                  style={{ color: 'var(--color-text)' }}>{selected.cuerpo}</pre>
              )}
            </div>

            {/* Panel IA (respuestas sugeridas) */}
            <div className="rounded-lg p-2"
              style={{ backgroundColor: 'var(--color-bg-card)' }}>
              <p className="text-xs mb-1.5" style={{ color: 'var(--color-text-secondary)' }}>
                Respuestas sugeridas por IA</p>
              <div className="flex gap-2">
                {['Responder', 'Agradecer', 'Más info'].map(s => (
                  <button key={s}
                    className="text-xs px-3 py-1.5 rounded-pill font-bold"
                    style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>
                    {s}
                  </button>
                ))}
              </div>
            </div>
          </>
        ) : (
          <div className="flex items-center justify-center h-full text-sm"
            style={{ color: 'var(--color-text-secondary)' }}>
            Selecciona un mensaje para verlo
          </div>
        )}
      </div>
    </div>
  );
}
