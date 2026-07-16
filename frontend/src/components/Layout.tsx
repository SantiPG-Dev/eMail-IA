import { useState, useEffect, useCallback, useMemo } from 'react';
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

interface CuentaConCarpetas {
  id: number;
  email: string;
  carpetas: CarpetaImap[];
}

// Máscara para nombres de carpetas IMAP
const MASCARA_CARPETAS: Record<string, string> = {
  'INBOX': '📥 INBOX principal',
  'Sent': '📤 Enviados',
  'Sent Messages': '📤 Enviados',
  'Sent Items': '📤 Enviados',
  'Drafts': '📝 Borradores',
  'Trash': '🗑 Papelera',
  'Bin': '🗑 Papelera',
  'Deleted': '🗑 Eliminados',
  'Deleted Items': '🗑 Eliminados',
  'Spam': '⚠️ No deseado',
  'Junk': '⚠️ No deseado',
  'Junk E-mail': '⚠️ No deseado',
  '[Gmail]/Spam': '⚠️ No deseado',
  '[Gmail]/Trash': '🗑 Papelera',
  '[Gmail]/All Mail': '📬 Todos',
  '[Gmail]/Starred': '⭐ Destacados',
  '[Gmail]/Important': '🔔 Importantes',
  '[Gmail]/Enviados': '📤 Enviados',
  '[Gmail]/Borradores': '📝 Borradores',
  '[Gmail]/Categoría Social': '👥 Social',
  '[Gmail]/Categoría Foros': '💬 Foros',
  '[Gmail]/Categoría Promociones': '🏷 Promociones',
  '[Gmail]/Categoría Actualizaciones': '🔄 Actualizaciones',
  'Archive': '📦 Archivo',
  'Archives': '📦 Archivo',
  '[Gmail]/Archive': '📦 Archivo',
  'Outbox': '📤 OUTBOX principal',
  '[Gmail]/Outbox': '📤 OUTBOX principal',
};

function nombreCarpetaLegible(nombre: string): string {
  return MASCARA_CARPETAS[nombre] || nombre;
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
  const [cuentasCarpetas, setCuentasCarpetas] = useState<CuentaConCarpetas[]>([]);
  const [cargandoCarpetas, setCargandoCarpetas] = useState(false);
  const [carpetaSeleccionada, setCarpetaSeleccionada] = useState('INBOX');
  const [inboxExpandido, setInboxExpandido] = useState(true);
  const [salidaExpandido, setSalidaExpandido] = useState(true);
  const [cuentasExpandidas, setCuentasExpandidas] = useState<Record<number, boolean>>({});

  const cargarCarpetas = useCallback(async () => {
    setCargandoCarpetas(true);
    try {
      const cuentas = await cuentaApi.list();
      if (cuentas.data.length > 0) {
        const resultados: CuentaConCarpetas[] = [];
        for (const cuenta of cuentas.data) {
          try {
            const resp = await cuentaApi.carpetas(cuenta.id);
            resultados.push({
              id: cuenta.id,
              email: cuenta.email,
              carpetas: resp.data?.length > 0 ? resp.data : [
                { nombre: 'INBOX', mensajes: 0, noLeidos: 0 },
                { nombre: 'Sent', mensajes: 0, noLeidos: 0 },
              ],
            });
          } catch {
            resultados.push({
              id: cuenta.id,
              email: cuenta.email,
              carpetas: [
                { nombre: 'INBOX', mensajes: 0, noLeidos: 0 },
                { nombre: 'Sent', mensajes: 0, noLeidos: 0 },
              ],
            });
          }
        }
        setCuentasCarpetas(resultados);
      }
    } catch {
      // Sin cuentas
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

  // Totales de no leídos
  const totalNoLeidos = useMemo(() =>
    cuentasCarpetas.reduce((sum, cc) =>
      sum + cc.carpetas.reduce((s, c) => s + (c.noLeidos || 0), 0), 0), [cuentasCarpetas]);

  const ppal = cuentasCarpetas[0];
  const inboxNoLeidos = useMemo(() =>
    ppal ? ppal.carpetas
      .filter(c => c.nombre === 'INBOX' || c.nombre.toUpperCase().includes('INBOX'))
      .reduce((s, c) => s + (c.noLeidos || 0), 0) : 0, [ppal]);

  const salidaNoLeidos = useMemo(() =>
    ppal ? ppal.carpetas
      .filter(c => c.nombre.toUpperCase().includes('OUTBOX') || c.nombre === 'Sent'
        || c.nombre === 'Sent Messages' || c.nombre === 'Sent Items' || c.nombre === '[Gmail]/Enviados')
      .reduce((s, c) => s + (c.noLeidos || 0), 0) : 0, [ppal]);

  const badge = (n: number) => n > 0
    ? <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-full ml-auto"
        style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>{n}</span>
    : null;

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
          {(() => {
            const activoIdx = NAV_ITEMS.findIndex(item =>
              item.key === 'correo' ? correoActivo : location.pathname.startsWith(item.to));
            const orden = activoIdx > 0
              ? [NAV_ITEMS[activoIdx], ...NAV_ITEMS.slice(0, activoIdx), ...NAV_ITEMS.slice(activoIdx + 1)]
              : NAV_ITEMS;
            return orden.map(item => (
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
                <span className="truncate">{item.label}</span>
                {item.key === 'correo' && badge(totalNoLeidos)}
              </NavLink>
              
              {/* Submenú de carpetas IMAP (solo visible cuando Correo está activo) */}
              {item.key === 'correo' && correoActivo && (
                <div className="ml-3 mt-0.5 mb-1 flex flex-col gap-0.5"
                  style={{ borderColor: 'var(--color-border)' }}>
                  
                  {cargandoCarpetas ? (
                    <span className="text-[10px] px-3 py-1 italic"
                      style={{ color: 'var(--color-text-secondary)' }}>
                      Cargando carpetas…
                    </span>
                  ) : cuentasCarpetas.length > 0 ? (
                    <div className="overflow-y-auto max-h-[40vh] pr-1">
                      {/* ── CUENTA PRINCIPAL: Bandeja de entrada ── */}
                      {(() => {
                        const ppal = cuentasCarpetas[0];
                        const inboxes = ppal.carpetas.filter(c =>
                          c.nombre === 'INBOX' || c.nombre.toUpperCase().includes('INBOX'));
                        const deSalida = ppal.carpetas.filter(c =>
                          c.nombre.toUpperCase().includes('OUTBOX') || c.nombre === 'Sent'
                          || c.nombre === 'Sent Messages' || c.nombre === 'Sent Items'
                          || c.nombre === '[Gmail]/Enviados');
                        const salidaNombres = new Set(deSalida.map(c => c.nombre));
                        const otras = ppal.carpetas.filter(c =>
                          !c.nombre.toUpperCase().includes('INBOX')
                          && !salidaNombres.has(c.nombre));
                        return <>
                          <button
                            onClick={() => setInboxExpandido(!inboxExpandido)}
                            className="flex items-center gap-1 px-3 py-1.5 text-[11px] rounded-lg text-left transition-colors hover:opacity-80 font-semibold"
                            style={{ color: 'var(--color-text)' }}
                          >
                            <span className="text-[10px] w-3 shrink-0">{inboxExpandido ? '▼' : '▶'}</span>
                            <span>📥 Bandeja de entrada</span>
                            {badge(inboxNoLeidos)}
                          </button>

                          {inboxExpandido && (
                            <div className="flex flex-col gap-0.5 ml-2"
                              style={{ borderLeft: '2px solid var(--color-border)' }}>
                              {inboxes.map(c => (
                                <button key={c.nombre}
                                  onClick={() => irACorreoCarpeta(c.nombre)}
                                  className="flex items-center gap-1 pl-5 pr-3 py-1 text-[11px] rounded-r-lg text-left transition-colors"
                                  style={{
                                    backgroundColor: carpetaSeleccionada === c.nombre
                                      ? 'var(--color-accent-selected)' : 'transparent',
                                    color: carpetaSeleccionada === c.nombre
                                      ? '#0F172A' : 'var(--color-text-muted)',
                                  }}>
                                  <span className="truncate flex-1">{nombreCarpetaLegible(c.nombre)}</span>
                                  {c.noLeidos > 0 && (
                                    <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-full"
                                      style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>{c.noLeidos}</span>
                                  )}
                                </button>
                              ))}
                            </div>
                          )}

                          {/* ── Bandeja de salida ── */}
                          {deSalida.length > 0 && (
                            <div className="flex flex-col gap-0.5">
                              <button
                                onClick={() => setSalidaExpandido(!salidaExpandido)}
                                className="flex items-center gap-1 px-3 py-1.5 text-[11px] rounded-lg text-left transition-colors hover:opacity-80 font-semibold"
                                style={{ color: 'var(--color-text)' }}
                              >
                                <span className="text-[10px] w-3 shrink-0">{salidaExpandido ? '▼' : '▶'}</span>
                                <span>📤 Bandeja de salida</span>
                                {badge(salidaNoLeidos)}
                              </button>
                              {salidaExpandido && (
                                <div className="flex flex-col gap-0.5 ml-2"
                                  style={{ borderLeft: '2px solid var(--color-border)' }}>
                                  {deSalida.map(c => (
                                    <button key={c.nombre}
                                      onClick={() => irACorreoCarpeta(c.nombre)}
                                      className="flex items-center gap-1 pl-5 pr-3 py-1 text-[11px] rounded-r-lg text-left transition-colors"
                                      style={{
                                        backgroundColor: carpetaSeleccionada === c.nombre
                                          ? 'var(--color-accent-selected)' : 'transparent',
                                        color: carpetaSeleccionada === c.nombre
                                          ? '#0F172A' : 'var(--color-text-muted)',
                                      }}>
                                      <span className="truncate flex-1">{nombreCarpetaLegible(c.nombre)}</span>
                                      {c.noLeidos > 0 && (
                                        <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-full"
                                          style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>{c.noLeidos}</span>
                                      )}
                                    </button>
                                  ))}
                                </div>
                              )}
                            </div>
                          )}

                          {/* Otras carpetas de la cuenta principal */}
                          {otras.map(c => (
                            <button key={c.nombre}
                              onClick={() => irACorreoCarpeta(c.nombre)}
                              className="flex items-center gap-1 px-3 py-1.5 text-[11px] rounded-lg text-left transition-colors"
                              style={{
                                backgroundColor: carpetaSeleccionada === c.nombre
                                  ? 'var(--color-accent-selected)' : 'transparent',
                                color: carpetaSeleccionada === c.nombre
                                  ? '#0F172A' : 'var(--color-text-muted)',
                              }}>
                              <span className="truncate flex-1">{nombreCarpetaLegible(c.nombre)}</span>
                              {c.noLeidos > 0 && (
                                <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-full"
                                  style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>{c.noLeidos}</span>
                              )}
                            </button>
                          ))}
                        </>;
                      })()}

                      {/* ── RECOLECTORES (todas las cuentas en lista plana) ── */}
                      {cuentasCarpetas.slice(1).map(cc => (
                        <div key={cc.id} className="mt-1">
                          <span className="flex items-center gap-1 px-3 py-1 text-[10px] font-semibold"
                            style={{ color: 'var(--color-text-secondary)' }}>
                            {cc.email}
                          </span>
                          <div className="flex flex-col gap-0.5">
                            {cc.carpetas.map(c => (
                              <button key={c.nombre}
                                onClick={() => irACorreoCarpeta(c.nombre)}
                                className="flex items-center gap-1 pl-6 pr-3 py-1 text-[10px] rounded-r-lg text-left transition-colors"
                                style={{
                                  backgroundColor: carpetaSeleccionada === c.nombre
                                    ? 'var(--color-accent-selected)' : 'transparent',
                                  color: carpetaSeleccionada === c.nombre
                                    ? '#0F172A' : 'var(--color-text-muted)',
                                }}>
                                <span className="truncate flex-1">{nombreCarpetaLegible(c.nombre)}</span>
                                {c.noLeidos > 0 && (
                                  <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-full"
                                    style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>{c.noLeidos}</span>
                                )}
                              </button>
                            ))}
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <span className="text-[10px] px-3 py-1 italic"
                      style={{ color: 'var(--color-text-secondary)' }}>
                      Sin carpetas
                    </span>
                  )}
                </div>
              )}
            </div>
          ));
          })()}
        </div>

        {/* === ESPACIADOR === */}
        <div className="flex-1 min-h-[8px]" />

        {/* === SEPARADOR + BOTTOM ITEMS (fijos al fondo) === */}
        <div className="px-2.5 pb-3.5 flex flex-col gap-1.5 shrink-0">
          <hr style={{ borderColor: 'var(--color-bg-card)' }} />
          
          {/* Configuración */}
          <NavLink to={BOTTOM_ITEMS[0].to}
            className={({ isActive }) =>
              `flex items-center px-3 py-2 text-sm rounded-lg transition-colors ${
                isActive ? 'font-bold' : 'hover:opacity-80'
              }`
            }
            style={({ isActive }) => ({
              backgroundColor: isActive ? 'var(--color-accent-selected)' : 'transparent',
              color: isActive ? '#0F172A' : 'var(--color-text-muted)',
            })}>
            {BOTTOM_ITEMS[0].label}
          </NavLink>

          {/* Chat IA + Cerrar sesión en línea */}
          <div className="flex items-center gap-1">
            <NavLink to={BOTTOM_ITEMS[1].to}
              className={({ isActive }) =>
                `flex items-center px-3 py-2 text-sm rounded-lg transition-colors flex-1 ${
                  isActive ? 'font-bold' : 'hover:opacity-80'
                }`
              }
              style={({ isActive }) => ({
                backgroundColor: isActive ? 'var(--color-accent-selected)' : 'transparent',
                color: isActive ? '#0F172A' : 'var(--color-text-muted)',
              })}>
              {BOTTOM_ITEMS[1].label}
            </NavLink>
            <button onClick={handleLogout}
              className="text-[10px] px-2 py-1.5 rounded-lg transition-colors shrink-0"
              style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text-secondary)' }}>
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
