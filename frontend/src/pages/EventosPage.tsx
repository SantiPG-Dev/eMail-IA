import { NavLink, Outlet } from 'react-router-dom';

// Contenedor de la seccion Eventos: una pestana para Calendario y otra para
// Tareas. Las dos vistas leen la misma tabla `agenda`, asi que lo creado en
// una aparece en la otra.
const PESTANAS = [
  { to: 'calendario', label: '📅 Calendario' },
  { to: 'tareas', label: '✅ Tareas' },
];

export default function EventosPage() {
  return (
    <div className="h-full flex flex-col min-w-0" style={{ backgroundColor: 'var(--color-bg)' }}>
      <div className="flex gap-1 px-4 pt-2 shrink-0 border-b"
        style={{ borderColor: 'var(--color-border)' }}>
        {PESTANAS.map(p => (
          <NavLink key={p.to} to={p.to}
            className="px-4 py-2 text-xs font-bold rounded-t-lg transition-colors hover:opacity-90"
            style={({ isActive }) => ({
              backgroundColor: isActive ? 'var(--color-bg-card)' : 'transparent',
              color: isActive ? 'var(--color-accent)' : 'var(--color-text-secondary)',
              borderBottom: isActive ? '2px solid var(--color-accent)' : '2px solid transparent',
            })}>
            {p.label}
          </NavLink>
        ))}
      </div>
      <div className="flex-1 min-h-0">
        <Outlet />
      </div>
    </div>
  );
}
