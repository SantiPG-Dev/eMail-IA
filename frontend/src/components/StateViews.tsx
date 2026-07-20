// Estados visuales reutilizables: carga, vacío y error.
// Usan las mismas CSS vars (--color-*) que el resto de la app.

export function Spinner({ label = 'Cargando...' }: { label?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-12">
      <div className="w-6 h-6 border-2 border-t-transparent rounded-full animate-spin"
        style={{ borderColor: 'var(--color-accent)', borderTopColor: 'transparent' }} />
      <p className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>{label}</p>
    </div>
  );
}

export function EmptyState({ icon = '📭', title, hint }: { icon?: string; title: string; hint?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-12 text-center px-4">
      <span className="text-3xl opacity-60">{icon}</span>
      <p className="text-sm font-medium" style={{ color: 'var(--color-text-secondary)' }}>{title}</p>
      {hint && <p className="text-xs" style={{ color: 'var(--color-text-muted)' }}>{hint}</p>}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-12 text-center px-4">
      <span className="text-3xl opacity-60">⚠️</span>
      <p className="text-sm font-medium" style={{ color: '#ef4444' }}>{message}</p>
      {onRetry && (
        <button onClick={onRetry}
          className="mt-1 px-3 py-1 text-xs font-bold rounded-pill"
          style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>
          Reintentar
        </button>
      )}
    </div>
  );
}
