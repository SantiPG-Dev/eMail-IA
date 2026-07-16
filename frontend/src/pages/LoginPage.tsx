import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme, THEMES } from '../context/ThemeContext';
import { authApi, cuentaApi } from '../api/client';
import AccountSetupModal from '../components/AccountSetupModal';

/* ===========================================================
   eMail-IA — LoginPage rediseñada
   Layout tres columnas:
     Izquierda │ Centro (logo + pass + botón abajo) │ Derecha
   Con cuentas → columnas laterales con tarjetas de perfil
   Sin cuentas → solo columna central centrada
   =========================================================== */

interface Cuenta {
  id: number;
  nombre: string;
  email: string;
  tipoConexion: string;
  servidor: string;
}

/* ── Componente reutilizable: tarjeta de perfil ── */
function PerfilCard({ cuenta, seleccionada, onSeleccionar }: { cuenta: Cuenta; seleccionada: boolean; onSeleccionar: () => void }) {
  const getInitials = (name: string) =>
    name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();

  return (
    <div
      className={`group flex flex-col items-center gap-2 p-4 rounded-2xl border
        cursor-pointer transition-all duration-200 hover:scale-105 hover:shadow-xl w-28 ${
        seleccionada ? 'ring-2 ring-offset-2' : ''
      }`}
      style={{
        backgroundColor: 'var(--color-bg-card)',
        borderColor: seleccionada ? 'var(--color-accent)' : 'var(--color-border)',
      }}
      onClick={onSeleccionar}
      title={seleccionada ? 'Cuenta seleccionada' : 'Seleccionar esta cuenta'}
    >
      {/* Avatar circular con iniciales */}
      <div
        className="w-12 h-12 rounded-full flex items-center justify-center
          text-sm font-bold shadow-inner"
        style={{
          background:
            cuenta.tipoConexion === 'POP3'
              ? 'linear-gradient(135deg, #FBBF24, #F59E0B)'
              : 'linear-gradient(135deg, #22D3EE, #06B6D4)',
          color: '#0F172A',
        }}
      >
        {getInitials(cuenta.nombre)}
      </div>

      <div className="text-center">
        <p className="text-xs font-bold truncate max-w-24"
          style={{ color: 'var(--color-text)' }}>
          {cuenta.nombre}
        </p>
        <p className="text-[10px] truncate max-w-24"
          style={{ color: 'var(--color-text-muted)' }}>
          {cuenta.email}
        </p>
      </div>

      <span
        className="text-[9px] px-2 py-0.5 rounded-full font-bold uppercase"
        style={{
          backgroundColor:
            cuenta.tipoConexion === 'POP3'
              ? 'rgba(251, 191, 36, 0.2)'
              : 'rgba(34, 211, 238, 0.2)',
          color: cuenta.tipoConexion === 'POP3' ? '#FBBF24' : '#22D3EE',
        }}
      >
        {cuenta.tipoConexion || 'IMAP'}
      </span>
    </div>
  );
}

/* ── Componente reutilizable: botón "+" añadir perfil ── */
function AddPerfilCard({ onClick }: { onClick: () => void }) {
  return (
    <div
      className="flex flex-col items-center justify-center gap-2 p-4 rounded-2xl
        border-2 border-dashed cursor-pointer transition-all duration-200
        hover:scale-105 w-28 h-[128px]"
      style={{
        borderColor: 'var(--color-border)',
        color: 'var(--color-text-muted)',
      }}
      onClick={onClick}
      title="Añadir nueva cuenta"
    >
      <span className="text-2xl leading-none">+</span>
      <span className="text-[10px] font-medium text-center">Añadir perfil</span>
    </div>
  );
}

export default function LoginPage() {
  const navigate = useNavigate();
  const { login, setup } = useAuth();
  const { mode, toggleMode, setTheme, theme } = useTheme();

  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState('');
  const [needsSetup, setNeedsSetup] = useState(false);
  const [cuentas, setCuentas] = useState<Cuenta[]>([]);
  const [showSetupModal, setShowSetupModal] = useState(false);
  const [cuentaSeleccionada, setCuentaSeleccionada] = useState<number | null>(null);
  const loadedRef = useRef(false);
  const inputRef = useRef<HTMLInputElement>(null);

  // Solo ejecutar una vez al montar
  useEffect(() => {
    if (loadedRef.current) return;
    loadedRef.current = true;

    let cargarCuentasOk = false;

    // Cargar estado y cuentas en paralelo
    Promise.all([
      // Si falla (401 sin token), catch devuelve vacío y marcamos error
      cuentaApi.list()
        .then(res => {
          cargarCuentasOk = true;
          return res.data || [];
        })
        .catch(() => {
          cargarCuentasOk = false;
          return [];
        }),
      authApi.status()
        .then(res => res.data || { configurada: false })
        .catch(() => ({ configurada: false }))
    ]).then(([lista, statusRes]: [any, any]) => {
      setCuentas(lista);
      const configurada = statusRes?.configurada === true;
      setNeedsSetup(!configurada);
      // Solo auto-abrir modal si: hay contraseña, se pudieron cargar cuentas, y no hay ninguna
      if (configurada && cargarCuentasOk && lista.length === 0) {
        setShowSetupModal(true);
      }
    });
  }, []);

  // Enfocar input al montar (con cleanup para evitar timebooks huérfanos)
  useEffect(() => {
    const timer = setTimeout(() => inputRef.current?.focus(), 300);
    return () => clearTimeout(timer);
  }, [needsSetup]);

  const handleSubmit = useCallback(async () => {
    setLoading(true);
    setStatus('');
    try {
      let ok: boolean;
      if (needsSetup) {
        ok = await setup(password);
        if (ok) {
          setStatus('Contraseña configurada. Iniciando sesión...');
          ok = await login(password);
        }
      } else {
        ok = await login(password);
      }
      if (ok) navigate('/', { replace: true });
      else setStatus(needsSetup ? 'Error al configurar' : 'Contraseña incorrecta');
    } catch {
      setStatus('Error de conexión con el servidor');
    } finally {
      setLoading(false);
    }
  }, [needsSetup, password, setup, login, navigate]);

  const handleAccountSaved = useCallback(async () => {
    try {
      const res = await cuentaApi.list();
      setCuentas(res.data || []);
    } catch { /* ignore */ }
    setShowSetupModal(false);
  }, []);

  const hasAccounts = cuentas.length > 0;
  const cuentaActiva = cuentas.find(c => c.id === cuentaSeleccionada);

  // Repartir cuentas entre columna izq y dcha
  const mitad = Math.ceil(cuentas.length / 2);
  const izquierda = cuentas.slice(0, mitad);
  const derecha = cuentas.slice(mitad);

  const seleccionarCuenta = (id: number) => {
    setCuentaSeleccionada(id);
    setStatus('');
    inputRef.current?.focus();
  };

  /* ── Render: columna de tarjetas ── */
  const renderColumna = (lista: Cuenta[]) => (
    <div className="flex flex-col items-center gap-3">
      <p className="text-[11px] font-semibold uppercase tracking-widest mb-1"
        style={{ color: 'var(--color-text-muted)' }}>
        Perfiles
      </p>
      {lista.map(c => (
        <PerfilCard
          key={c.id}
          cuenta={c}
          seleccionada={c.id === cuentaSeleccionada}
          onSeleccionar={() => seleccionarCuenta(c.id)}
        />
      ))}
      <AddPerfilCard onClick={() => setShowSetupModal(true)} />
    </div>
  );

  return (
    <div className="min-h-screen flex items-center justify-center relative overflow-hidden"
      style={{ backgroundColor: 'var(--color-bg)' }}>

      {/* Decoración: gradiente sutil de fondo (clase CSS, evita repaints inline) */}
      <div className="absolute inset-0 pointer-events-none login-gradient" />

      {/* ── LAYOUT TRES COLUMNAS ── */}
      <div className="relative z-10 flex items-start justify-center gap-8 w-full max-w-5xl px-6">

        {/* COLUMNA IZQUIERDA — tarjetas */}
        {hasAccounts && (
          <div className="w-28 shrink-0 pt-8">{renderColumna(izquierda)}</div>
        )}

        {/* COLUMNA CENTRAL — logo + formulario */}
        <div className="flex flex-col items-center gap-6 w-full max-w-sm">

          {/* Logo grande sin fondo */}
          <div className="flex items-center justify-center">
            <img src="/logo.png" alt="eMail-IA"
              className="w-96 object-contain" />
          </div>

          {/* Texto de estado */}
          {needsSetup ? (
            <div className="text-center space-y-1">
              <h1 className="text-lg font-bold" style={{ color: 'var(--color-accent)' }}>
                🆕 Primer acceso
              </h1>
              <p className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>
                Crea una contraseña maestra para proteger tus datos
              </p>
            </div>
          ) : cuentaActiva ? (
            <div className="text-center space-y-1">
              <p className="text-xs" style={{ color: 'var(--color-text-muted)' }}>
                Cuenta seleccionada
              </p>
              <h1 className="text-base font-bold" style={{ color: 'var(--color-text)' }}>
                {cuentaActiva.email}
              </h1>
              <p className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
                Introduce tu contraseña maestra
              </p>
            </div>
          ) : (
            <div className="text-center space-y-1">
              <h1 className="text-lg font-bold" style={{ color: 'var(--color-text)' }}>
                {hasAccounts ? 'Selecciona un perfil' : '🔐 Inicia sesión'}
              </h1>
              <p className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>
                {hasAccounts
                  ? 'Toca una cuenta de la izquierda o derecha para empezar'
                  : 'Introduce tu contraseña maestra'}
              </p>
            </div>
          )}

          {/* Input de contraseña */}
          <input
            ref={inputRef}
            type="password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSubmit()}
            placeholder={needsSetup ? 'Crea una contraseña maestra' : 'Introduce la contraseña maestra'}
            className="w-full px-4 py-3 rounded-xl border text-sm outline-none
              transition-all duration-200 placeholder:opacity-50
              focus:ring-2 focus:ring-offset-2
              bg-[var(--color-bg)] text-[var(--color-text)]
              border-[var(--color-border)]
              focus:border-[var(--color-accent)] focus:ring-[var(--color-accent)]/30"
            autoComplete="off"
            disabled={loading}
          />

          {/* Botón único (abajo, mismo estilo siempre) */}
          <button
            onClick={handleSubmit}
            disabled={loading || !password}
            className="w-full py-3 rounded-xl font-bold text-sm
              transition-all duration-200
              disabled:opacity-40 disabled:cursor-not-allowed
              hover:brightness-110 active:scale-[0.98]
              text-white shadow-lg shadow-blue-500/20"
            style={{
              background: 'linear-gradient(135deg, #3B82F6, #2563EB)',
            }}
          >
            {loading ? (
              <span className="flex items-center justify-center gap-2">
                <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10"
                    stroke="currentColor" strokeWidth="4" fill="none" />
                  <path className="opacity-75" fill="currentColor"
                    d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
                </svg>
                Procesando...
              </span>
            ) : (
              'Entrar'
            )}
          </button>

          {/* Status */}
          {status && (
            <p className={`text-xs text-center font-medium ${
              status.includes('Error') || status.includes('incorrecta')
                ? 'text-red-500'
                : 'text-[var(--color-accent)]'
            }`}>
              {status}
            </p>
          )}

          {/* Selector de tema */}
          <div className="flex items-center gap-2 py-2">
            <span className="text-[11px]" style={{ color: 'var(--color-text-muted)' }}>☾</span>
            <button onClick={toggleMode}
              className="w-9 h-5 rounded-full relative transition-colors flex items-center"
              style={{ backgroundColor: mode === 'dark' ? '#475569' : '#94A3B8' }}>
              <span className="w-3.5 h-3.5 rounded-full bg-white transition-all duration-200 mx-0.5"
                style={{ marginLeft: mode === 'dark' ? '0.125rem' : 'auto' }} />
            </button>
            <span className="text-[11px]" style={{ color: 'var(--color-text-muted)' }}>☼</span>

            <div className="w-px h-3 mx-1.5" style={{ backgroundColor: 'var(--color-border)' }} />

            <select value={theme} onChange={e => setTheme(e.target.value as any)}
              className="text-[10px] px-1.5 py-1 rounded border outline-none cursor-pointer
                bg-[var(--color-bg-card)] text-[var(--color-text)] border-[var(--color-border)]">
              {THEMES.map(t => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </div>
        </div>

        {/* COLUMNA DERECHA — tarjetas */}
        {hasAccounts && (
          <div className="w-28 shrink-0 pt-8">{renderColumna(derecha)}</div>
        )}
      </div>

      <AccountSetupModal open={showSetupModal} onClose={handleAccountSaved} />
    </div>
  );
}
