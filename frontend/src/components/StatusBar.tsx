import { useSync } from '../context/SyncContext';

export default function StatusBar() {
  const { syncing, progress, statusText, totalMessages, accountEmail } = useSync();

  return (
    <div className="flex items-center gap-2 px-3 py-1 text-[10px] border-t shrink-0 min-h-[24px]"
      style={{ backgroundColor: 'var(--color-bg-sidebar)', borderColor: 'var(--color-border)',
               color: 'var(--color-text-secondary)' }}>
      
      {/* Cuenta */}
      <span className="shrink-0" title="Cuenta activa">📧 {accountEmail}</span>

      {/* Barra de progreso sincronización */}
      {syncing && (
        <div className="flex items-center gap-1.5 flex-1">
          <div className="flex-1 h-1.5 rounded-full overflow-hidden max-w-[200px]"
            style={{ backgroundColor: 'var(--color-bg-elevated)' }}>
            <div className="h-full rounded-full transition-all duration-500"
              style={{ width: `${progress}%`, backgroundColor: 'var(--color-accent)' }} />
          </div>
          <span className="text-[9px] whitespace-nowrap">{statusText}</span>
        </div>
      )}

      {/* Último estado (cuando no está sincronizando) */}
      {!syncing && statusText !== 'Inactivo' && (
        <span className="flex-1 text-[9px]">{statusText}</span>
      )}

      <span className="flex-1" />

      {/* Mensajes totales */}
      {totalMessages > 0 && <span>{totalMessages} msgs</span>}
    </div>
  );
}
