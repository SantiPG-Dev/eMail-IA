import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import StatusBar from './StatusBar';

const NAV_ITEMS = [
  { to: '/correo', label: '📬 Correo' },
  { to: '/calendario', label: '📅 Calendario' },
  { to: '/contactos', label: '👤 Contactos' },
  { to: '/tareas', label: '✅ Tareas' },
];

const BOTTOM_ITEMS = [
  { to: '/config', label: '⚙️ Configuración' },
  { to: '/chat-ia', label: '✨ Chat IA' },
];

export default function Layout() {
  const { logout } = useAuth();
  const { mode, toggleMode } = useTheme();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="flex h-screen overflow-hidden" style={{
      backgroundColor: 'var(--color-bg)',
      color: 'var(--color-text)',
    }}>
      {/* Sidebar — replica main-view.fxml del JavaFX */}
      <aside className="flex flex-col w-[232px] min-w-[200px] max-w-[280px] shrink-0"
        style={{ backgroundColor: 'var(--color-bg-sidebar)' }}>
        {/* Logo + Brand */}
        <div className="flex flex-col items-center gap-2.5 px-2.5 pt-3.5 pb-3.5">
          <img src="/logo.png" alt="eMail-IA"
          className="w-[168px] object-contain" />
        </div>

        {/* Nav Menu */}
        <nav className="flex flex-col gap-1 px-2.5 flex-1">
          <p className="text-xs font-semibold px-1 pb-1.5 pt-3.5"
            style={{ color: 'var(--color-text-secondary)' }}>
            Menú
          </p>

          {NAV_ITEMS.map(item => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex items-center px-3 py-2.5 text-sm rounded-lg transition-colors ${
                  isActive
                    ? 'font-bold'
                    : 'hover:opacity-80'
                }`
              }
              style={({ isActive }) => ({
                backgroundColor: isActive ? 'var(--color-accent-selected)' : 'transparent',
                color: isActive ? '#0F172A' : 'var(--color-text-muted)',
              })}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* Bottom items */}
        <div className="px-2.5 pb-3.5 flex flex-col gap-1.5">
          <hr style={{ borderColor: 'var(--color-bg-card)' }} />
          {BOTTOM_ITEMS.map(item => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex items-center px-3 py-2 text-sm rounded-lg transition-colors ${
                  isActive ? 'font-bold' : 'hover:opacity-80'
                }`
              }
              style={({ isActive }) => ({
                backgroundColor: isActive ? 'var(--color-accent-selected)' : 'transparent',
                color: isActive ? '#0F172A' : 'var(--color-text-muted)',
              })}
            >
              {item.label}
            </NavLink>
          ))}

          {/* Theme toggle + Logout */}
          <div className="flex items-center gap-2 px-1 pt-2">
            <button
              onClick={toggleMode}
              className="text-xs px-2 py-1 rounded transition-colors"
              style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text-secondary)' }}
            >
              {mode === 'dark' ? '☼ Claro' : '☾ Oscuro'}
            </button>
            <button
              onClick={handleLogout}
              className="text-xs px-2 py-1 rounded transition-colors ml-auto"
              style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text-secondary)' }}
            >
              Cerrar sesión
            </button>
          </div>
        </div>
      </aside>

      {/* Contenido principal */}
      <main className="flex-1 overflow-hidden flex flex-col">
        <div className="flex-1 overflow-hidden">
          <Outlet />
        </div>
        <StatusBar />
      </main>
    </div>
  );
}
