import { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import api, { mensajeApi, cuentaApi } from '../api/client';
import { useSync } from '../context/SyncContext';
import ComposePage from './ComposePage';

interface Mensaje {
  id: number; uid: string; remitente: string; asunto: string;
  cuerpo: string; html: string; categoria: string; prioridad: string;
  fechaRecepcion: string; destinatarios?: string;
}

export default function CorreoPage() {
  const [mensajes, setMensajes] = useState<Mensaje[]>([]);
  const { triggerSync, refreshMessages, syncing, progress, statusText, refreshKey } = useSync();
  const [selected, setSelected] = useState<Mensaje | null>(null);
  const [search, setSearch] = useState('');
  const [hasAccounts, setHasAccounts] = useState(false);
  const [cuentaHash, setCuentaHash] = useState('local');
  const [composeOpen, setComposeOpen] = useState(false);
  const [composeMode, setComposeMode] = useState<'nuevo' | 'responder' | 'reenviar'>('nuevo');
  const [composeTo, setComposeTo] = useState('');

  // Leer carpeta seleccionada desde query param (?carpeta=INBOX) pasado por el Layout
  const [searchParams] = useSearchParams();
  const carpetaImap = searchParams.get('carpeta') || 'INBOX';

  const cargarMensajes = useCallback(async (carpeta?: string) => {
    try {
      const cuentas = await cuentaApi.list();
      if (cuentas.data.length > 0) {
        const c = cuentas.data[0];
        setCuentaHash(c.email);
        const carpetaActual = carpeta || carpetaImap;
        const res = await mensajeApi.list(c.email, carpetaActual);
        setMensajes(res.data.mensajes || []);
      }
    } catch {}
  }, [carpetaImap]);

  // Carga inicial y cuando cambia la carpeta
  useEffect(() => {
    cuentaApi.list().then(r => {
      setHasAccounts(r.data.length > 0);
      if (r.data.length > 0) {
        const c = r.data[0];
        setCuentaHash(c.email);
        mensajeApi.list(c.email, carpetaImap).then(res => setMensajes(res.data.mensajes || [])).catch(() => {});
      }
    }).catch(() => {});
  }, [carpetaImap]);

  // Recargar mensajes cuando SyncContext completa un sync
  useEffect(() => {
    if (refreshKey > 0) {
      cuentaApi.list().then(r => {
        if (r.data.length > 0) {
          mensajeApi.list(r.data[0].email, carpetaImap).then(res => setMensajes(res.data.mensajes || [])).catch(() => {});
        }
      }).catch(() => {});
    }
  }, [refreshKey, carpetaImap]);

  const sincronizar = async () => {
    await triggerSync();
    await cargarMensajes(carpetaImap);
  };

  const handleSearch = async () => {
    if (!search.trim()) { await cargarMensajes(carpetaImap); return; }
    try {
      const res = await mensajeApi.search(cuentaHash, search);
      setMensajes(res.data.mensajes || []);
    } catch { /* error silencioso */ }
  };

  const eliminarMensaje = async () => {
    if (!selected) return;
    try {
      await mensajeApi.delete(selected.id);
      setMensajes(prev => prev.filter(m => m.id !== selected.id));
      setSelected(null);
    } catch { /* error silencioso */ }
  };

  const abrirCompose = (mode: 'nuevo' | 'responder' | 'reenviar') => {
    if (mode === 'responder' && selected) {
      setComposeTo(selected.remitente || '');
    } else if (mode === 'reenviar' && selected) {
      setComposeTo('');
    } else {
      setComposeTo('');
    }
    setComposeMode(mode);
    setComposeOpen(true);
  };

  const categoriaBorder = (cat: string) => {
    switch (cat) {
      case 'SPAM': case 'PHISHING': return '2px solid #ef4444';
      case 'LEGITIMO': return '2px solid #22c55e';
      default: return '2px solid #fbbf24';
    }
  };

  const categoriaBg = (cat: string) => {
    switch (cat) {
      case 'SPAM': case 'PHISHING': return '#2d1619';
      case 'LEGITIMO': return '#13281b';
      default: return '#2b2412';
    }
  };

  if (composeOpen) {
    return <ComposePage
      mode={composeMode}
      to={composeTo}
      subject={composeMode === 'responder' ? (selected ? 'Re: ' + selected.asunto : '') : ''}
      body={composeMode === 'reenviar' && selected ? '\n\n--- Mensaje original ---\n' + (selected.cuerpo || '') : ''}
      onClose={() => { setComposeOpen(false); cargarMensajes(carpetaImap); }}
    />;
  }

  return (
    <div className="flex h-full">
      {/* Panel izquierdo: lista */}
      <div className="w-[340px] min-w-[260px] flex flex-col gap-2 p-2.5"
        style={{ backgroundColor: 'var(--color-bg)' }}>
        {/* Botones superiores: Redactar + Borrar */}
        <div className="flex gap-1.5">
          <button onClick={() => abrirCompose('nuevo')}
            className="px-3 py-1.5 text-xs font-bold rounded-pill"
            style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>Redactar</button>
          <div className="flex-1" />
          <button onClick={eliminarMensaje} disabled={!selected}
            className="px-3 py-1.5 text-xs font-bold rounded-pill disabled:opacity-30"
            style={{ backgroundColor: '#ef4444', color: 'white' }}>Borrar</button>
        </div>

        {/* Buscador + Sync */}
        <div className="flex gap-1">
          <input value={search} onChange={e => setSearch(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSearch()}
            placeholder="Buscar..."
            className="flex-1 px-2 py-1.5 text-xs rounded-lg border outline-none"
            style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)', borderColor: 'var(--color-border)' }} />
          <button onClick={handleSearch}
            className="px-2 py-1.5 text-xs font-bold rounded-pill"
            style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>🔍</button>
          <button onClick={sincronizar} disabled={syncing}
            className="px-2 py-1.5 text-xs font-bold rounded-pill disabled:opacity-50"
            style={{ backgroundColor: syncing ? '#94A3B8' : '#22D3EE', color: '#0F172A' }}>
            {syncing ? '⏳' : '⬇'}
          </button>
        </div>

        {statusText !== 'Inactivo' && (
          <p className="text-xs" style={{ color: statusText.includes('Error') ? '#ef4444' : 'var(--color-accent)' }}>
            {statusText}
          </p>
        )}

        <div className="flex-1 overflow-y-auto space-y-1">
          {mensajes.map(m => (
            <div key={m.id} onClick={() => setSelected(m)}
              className="px-2.5 py-1.5 rounded-lg cursor-pointer text-xs"
              style={{
                border: categoriaBorder(m.categoria),
                backgroundColor: selected?.id === m.id ? 'var(--color-accent-selected)' : categoriaBg(m.categoria),
                color: selected?.id === m.id ? '#0F172A' : 'var(--color-text)',
              }}>
              <div className="font-bold truncate">{m.remitente || '(sin remitente)'}</div>
              <div className="truncate opacity-80">{m.asunto}</div>
              <div className="text-[10px] opacity-60">{m.fechaRecepcion?.slice(0, 10)}</div>
            </div>
          ))}
          {mensajes.length === 0 && !syncing && (
            <p className="text-xs text-center mt-4" style={{ color: 'var(--color-text-secondary)' }}>
              {hasAccounts ? 'Pulsa ⬇ para sincronizar' : 'Añade una cuenta en Configuración'}
            </p>
          )}
        </div>
      </div>

      {/* Panel derecho: detalle */}
      <div className="flex-1 flex flex-col p-2.5 gap-2 overflow-hidden"
        style={{ backgroundColor: 'var(--color-bg)' }}>
        {selected ? (
          <>
            {/* Botones de acción */}
            <div className="flex gap-1.5 flex-wrap">
              <button onClick={() => abrirCompose('responder')}
                className="px-3 py-1 text-xs font-bold rounded-pill"
                style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>Responder</button>
              <button onClick={() => abrirCompose('responder')}
                className="px-3 py-1 text-xs rounded-pill"
                style={{ backgroundColor: 'var(--color-bg-elevated)', color: 'var(--color-text)' }}>Resp. todos</button>
              <button onClick={() => abrirCompose('reenviar')}
                className="px-3 py-1 text-xs rounded-pill"
                style={{ backgroundColor: 'var(--color-bg-elevated)', color: 'var(--color-text)' }}>Reenviar</button>
              <div className="flex-1" />
              <button onClick={eliminarMensaje}
                className="px-2 py-1 text-xs rounded-pill"
                style={{ backgroundColor: '#64748B', color: 'white' }}>🗑 Papelera</button>
              <button onClick={async () => {
                if (!selected) return;
                try {
                  const res = await mensajeApi.classify(selected.id, 'SPAM');
                  setSelected(res.data);
                  await cargarMensajes(carpetaImap);
                } catch {}
              }}
                className="px-2 py-1 text-xs rounded-pill"
                style={{ backgroundColor: '#ef4444', color: 'white' }}>🚫 SPAM</button>
              <button onClick={async () => {
                if (!selected) return;
                try {
                  const res = await mensajeApi.classify(selected.id, 'LEGITIMO');
                  setSelected(res.data);
                  await cargarMensajes(carpetaImap);
                } catch {}
              }}
                className="px-2 py-1 text-xs rounded-pill"
                style={{ backgroundColor: '#22c55e', color: 'white' }}>✅ Legít</button>
            </div>

            {/* Asunto + remitente */}
            <h3 className="text-base font-bold" style={{ color: 'var(--color-accent-selected)' }}>
              {selected.asunto}</h3>
            <div className="flex items-center gap-2 text-xs" style={{ color: 'var(--color-text-secondary)' }}>
              <span>{selected.remitente}</span>
              <span>·</span>
              <span>{selected.fechaRecepcion?.slice(0, 10)}</span>
              {selected.categoria && selected.categoria !== 'DESCONOCIDO' && (
                <span className="px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider"
                  style={{
                    backgroundColor: selected.categoria === 'SPAM' || selected.categoria === 'PHISHING'
                      ? '#dc2626' : selected.categoria === 'LEGITIMO' ? '#16a34a' : '#ca8a04',
                    color: '#fff',
                    border: selected.categoria === 'SPAM' || selected.categoria === 'PHISHING'
                      ? '1px solid #ef4444' : selected.categoria === 'LEGITIMO' ? '1px solid #22c55e' : '1px solid #fbbf24',
                  }}>
                  {selected.categoria}
                </span>
              )}
            </div>

            {/* Cuerpo */}
            <div className="flex-1 overflow-auto rounded-lg p-2"
              style={{ backgroundColor: 'var(--color-bg-card)' }}>
              {selected.html ? (
                <iframe srcDoc={selected.html} className="w-full h-full border-0" title="Cuerpo" sandbox="" />
              ) : (
                <pre className="text-sm whitespace-pre-wrap font-sans" style={{ color: 'var(--color-text)' }}>
                  {selected.cuerpo}</pre>
              )}
            </div>

            {/* IA suggestions */}
            <div className="rounded-lg p-2" style={{ backgroundColor: 'var(--color-bg-card)' }}>
              <p className="text-[10px] mb-1" style={{ color: 'var(--color-text-secondary)' }}>Respuestas IA</p>
              <div className="flex gap-1.5">
                {['Responder', 'Agradecer', '+Info'].map(s => (
                  <button key={s}
                    className="text-[10px] px-2.5 py-1 rounded-pill font-bold"
                    style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>{s}</button>
                ))}
              </div>
            </div>
          </>
        ) : (
          <div className="flex items-center justify-center h-full text-sm"
            style={{ color: 'var(--color-text-secondary)' }}>
            Selecciona un mensaje
          </div>
        )}
      </div>
    </div>
  );
}
