import { useEffect, useRef } from 'react';

// Menú contextual flotante (botón derecho). Se cierra con click fuera,
// Escape o al elegir una opción.
export interface MenuItem {
  label: string;
  icon?: string;
  onClick: () => void;
  danger?: boolean;
}

interface ContextMenuProps {
  x: number;
  y: number;
  items: MenuItem[];
  onClose: () => void;
}

export default function ContextMenu({ x, y, items, onClose }: ContextMenuProps) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const clickFuera = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose();
    };
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    // En el siguiente tick: que el propio click que abre el menú no lo cierre
    const timer = setTimeout(() => document.addEventListener('mousedown', clickFuera), 0);
    document.addEventListener('keydown', esc);
    return () => {
      clearTimeout(timer);
      document.removeEventListener('mousedown', clickFuera);
      document.removeEventListener('keydown', esc);
    };
  }, [onClose]);

  // Evitar que el menú se salga de la ventana
  const estilo: React.CSSProperties = {
    left: Math.min(x, window.innerWidth - 210),
    top: Math.min(y, window.innerHeight - items.length * 34 - 16),
  };

  return (
    <div ref={ref} style={estilo}
      className="fixed z-[100] min-w-[200px] rounded-lg border shadow-2xl py-1"
      onMouseDown={e => e.stopPropagation()}>
      {items.map(item => (
        <button key={item.label}
          onClick={() => { onClose(); item.onClick(); }}
          className="w-full text-left px-3 py-1.5 text-xs flex items-center gap-2 hover:brightness-125 transition-filter"
          style={{
            backgroundColor: 'var(--color-bg-card)',
            color: item.danger ? '#ef4444' : 'var(--color-text)',
            borderColor: 'var(--color-border)',
          }}>
          {item.icon && <span aria-hidden>{item.icon}</span>}
          <span className="font-bold">{item.label}</span>
        </button>
      ))}
    </div>
  );
}
