import { useState, useEffect } from 'react';
import { useSync } from '../context/SyncContext';
import api from '../api/client';

type BackendState = {
  online: boolean;
  uptime: string;
  memoria: string;
  sync: string;
  ultimoError: string;
};

// Barra de estado inferior: muestra estado del backend (polling cada 10s),
// sincronización y contador de mensajes.
export default function StatusBar() {
  const { syncing, statusText, totalMessages, accountEmail } = useSync();
  const [backend, setBackend] = useState<BackendState>({
    online: false, uptime: '-', memoria: '-', sync: '-', ultimoError: '',
  });
  const [backendErr, setBackendErr] = useState('');

  // Poll backend status cada 10s
  useEffect(() => {
    let mounted = true;
    const poll = async () => {
      try {
        const res = await api.get('/api/status');
        if (mounted && res.data) {
          const d = res.data;
          setBackend({
            online: true,
            uptime: d.uptime || '-',
            memoria: d.memoria || '-',
            sync: d.sync || '-',
            ultimoError: '',
          });
          setBackendErr('');
        }
      } catch (e: any) {
        if (mounted) {
          const msg = e?.message || 'sin conexión';
          setBackend(s => ({ ...s, online: false }));
          setBackendErr(msg);
        }
      }
    };
    poll();
    const interval = setInterval(poll, 10000);
    return () => { mounted = false; clearInterval(interval); };
  }, []);

  const fe = backendErr || (statusText?.startsWith('⚠️') ? statusText.slice(2).trim() : '');
  const hayErrorFront = statusText?.startsWith('⚠️') ?? false;
  const hayErrorBack = !backend.online;

  return (
    <div className="flex items-center gap-2 px-3 py-1 text-[10px] border-t shrink-0 min-h-[24px]"
      style={{ backgroundColor: 'var(--color-bg-sidebar)', borderColor: 'var(--color-border)',
               color: 'var(--color-text-secondary)' }}>

      {/* ── IZQUIERDA: FRONTEND ── */}
      <span className="flex items-center gap-1 shrink-0 font-medium" title="Estado del frontend">
        <span className={`w-1.5 h-1.5 rounded-full ${hayErrorFront ? 'bg-red-500' : 'bg-green-500'}`} />
        Front
      </span>

      <span className="opacity-30">|</span>

      {/* Cuenta activa */}
      <span className="shrink-0" title="Cuenta activa">📧 {accountEmail}</span>

      <span className="opacity-30">|</span>

      {/* Estado de sync */}
      {syncing ? (
        <span className="flex items-center gap-1.5">
          <span className="w-2 h-2 rounded-full animate-pulse"
            style={{ backgroundColor: 'var(--color-accent)' }} />
          <span className={`text-[9px] ${hayErrorFront ? 'text-red-400' : ''}`}>{statusText || 'sincronizando...'}</span>
        </span>
      ) : (
        <span className={`text-[9px] truncate max-w-[200px] ${hayErrorFront ? 'text-red-400' : ''}`}
          title={statusText}>
          {statusText || 'inactivo'}
        </span>
      )}

      <span className="flex-1" />

      {/* ── DERECHA: BACKEND ── */}
      <span className={`flex items-center gap-1 shrink-0 font-medium ${hayErrorBack ? 'text-red-400' : ''}`}
        title={hayErrorBack ? `Backend offline: ${backendErr}` : 'Estado del backend'}>
        <span className={`w-1.5 h-1.5 rounded-full ${hayErrorBack ? 'bg-red-500' : 'bg-green-500'}`} />
        Back
      </span>

      <span className="opacity-30">|</span>

      {/* Info del backend */}
      <span className="text-[9px] shrink-0" title={`Sync: ${backend.sync}`}>
        {backend.online
          ? `⏱ ${backend.uptime} · ${backend.memoria}`
          : `⚠️ ${backendErr}`}
      </span>

      {/* Mensajes totales */}
      <span className="shrink-0 font-medium" title="Total mensajes locales">
        {totalMessages > 0 ? `${totalMessages} msgs` : '0 msgs'}
      </span>
    </div>
  );
}
