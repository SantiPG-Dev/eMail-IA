import { useState, useEffect, useCallback } from 'react';
import { NavLink, Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { cuentaApi } from '../api/client';
import StatusBar from './StatusBar';

interface CarpetaImap {
  nombre: string;
  mensajes: number;
  noLeidos: number;
}

const NAV_ITEMS = [
  { to: '/correo', label: '📬 Correo', key: 'correo' },
  { to: '/calendario', label: '📅 Calendario', key: 'calendario' },
  { to: '/contactos', label: '👤 Contactos', key: 'contactos' },
  { to: '/tareas', label: '✅ Tareas', key: 'tareas' },
];

const BOTTOM_ITEMS = [
  { to: '/config', label: '⚙️ Configuración' },
  { to: '/chat-ia', label: '✨ Chat IA' },
];

export default function Layout() {
  const { logout } = useAuth();
  const { mode, toggleMode } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();

  const correoActivo = location.pathname.startsWith('/correo');
  const [carpetasImap, setCarpetasImap] = useState<CarpetaImap[]>([]);
  const [cargandoCarpetas, setCargandoCarpetas] = useState(false);
  const [carpetaSeleccionada, setCarpetaSeleccionada] = useState('INBOX');

  const cargarCarpetas = useCallback(async () => {
    setCargandoCarpetas(true);
    try {
      const cuentas = await cuentaApi.list();
      if (cuentas.data.length > 0) {
        const cuenta = cuentas.data[0];
        // Usar endpoint real de carpetas IMAP
        const resp = await cuentaApi.carpetas(cuenta.id);
        if (resp.data && resp.data.length > 0) {
          setCarpetasImap(resp.data);
        } else {
          setCarpetasImap([
            { nombre: 'INBOX', mensajes: 0, noLeidos: 0 },
            { nombre: 'Sent', mensajes: 0, noLeidos: 0 },
          ]);
        }
      }
    } catch {
      setCarpetasImap([
        { nombre: 'INBOX', mensajes: 0, noLeidos: 0 },
        { nombre: 'Sent', mensajes: 0, noLeidos: 0 },
      ]);
    } finally {
      setCargandoCarpetas(false);
    }
  }, []);

  // Cargar carpetas cuando se activa Correo
  useEffect(() => {
    if (correoActivo) {
      cargarCarpetas();
    }
  }, [correoActivo, cargarCarpetas]);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const irACorreoCarpeta = (carpeta: string) => {
    setCarpetaSeleccionada(carpeta);
    navigate(`/correo?carpeta=${encodeURIComponent(carpeta)}`);
  };

  return (
    <div className="flex h-screen overflow-hidden" style={{
      backgroundColor: 'var(--color-bg)',
      color: 'var(--color-text)',
    }}>
      {/* Sidebar — replica exacta de main-view.fxml del JavaFX */}
      <aside className="flex flex-col w-[232px] min-w-[200px] max-w-[280px] shrink-0"
        style={{ backgroundColor: 'var(--color-bg-sidebar)' }}>
        
        {/* === SECCIÓN SUPERIOR: Logo + Brand (fijo) === */}
        <div className="flex flex-col items-center gap-2.5 px-2.5 pt-3.5 pb-3.5 shrink-0">
          <img src="/logo.png" alt="eMail-IA"
            className="w-[168px] object-contain" />
        </div>

        {/* === CABECERA "Menú" === */}
        <p className="text-xs font-semibold px-4 pb-1.5 shrink-0"
          style={{ color: 'var(--color-text-secondary)' }}>
          Menú
        </p>

        {/* === ZONA SCROLLABLE: Nav items + submenú Correo === */}
        <div className="flex flex-col gap-0.5 px-2.5 overflow-y-auto shrink min-h-0">
          {NAV_ITEMS.map(item => (
            <div key={item.to}>
              <NavLink
                to={item.to}
                end={item.key === 'correo'}
                className={({ isActive }) =>
                  `flex items-center px-3 py-2.5 text-sm rounded-lg transition-colors ${
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
              
              {/* Submenú de carpetas IMAP (solo visible cuando Correo está activo) */}
              {item.key === 'correo' && correoActivo && (
                <div className="ml-3 mt-0.5 mb-1 flex flex-col gap-0.5 border-l-2"
                  style={{ borderColor: 'var(--color-border)' }}>
                  {cargandoCarpetas ? (
                    <span className="text-[10px] px-3 py-1 italic"
                      style={{ color: 'var(--color-text-secondary)' }}>
                      Cargando carpetas…
                    </span>
                  ) : carpetasImap.length > 0 ? (
                    carpetasImap.map(c => (
                      <button
                        key={c.nombre}
                        onClick={() => irACorreoCarpeta(c.nombre)}
                        className="flex items-center gap-1 px-3 py-1.5 text-[11px] rounded-r-lg text-left transition-colors"
                        style={{
                          backgroundColor: carpetaSeleccionada === c.nombre
                            ? 'var(--color-accent-selected)'
                            : 'transparent',
                          color: carpetaSeleccionada === c.nombre
                            ? '#0F172A'
                            : 'var(--color-text-muted)',
                        }}
                      >
                        <span className="truncate flex-1">{c.nombre}</span>
                        {c.noLeidos > 0 && (
                          <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-full"
                            style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>
                            {c.noLeidos}
                          </span>
                        )}
                      </button>
                    ))
                  ) : (
                    <span className="text-[10px] px-3 py-1 italic"
                      style={{ color: 'var(--color-text-secondary)' }}>
                      Sin carpetas
                    </span>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>

        {/* === ESPACIADOR FLEXIBLE (equivalente a <Region VBox.vgrow="ALWAYS"/> en JavaFX) === */}
        <div className="flex-1 min-h-[8px]" />

        {/* === SEPARADOR + ITEMS INFERIORES (fijos al fondo) === */}
        <div className="px-2.5 pb-3.5 flex flex-col gap-1.5 shrink-0">
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
