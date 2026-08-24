import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { cuentaApi } from '../api/client';
import AccountSetupModal from '../components/AccountSetupModal';

// Login sin contraseña maestra. Seleccionas tu cuenta de correo y
// entras tu password IMAP — esa misma password te loguea en la app.

interface Cuenta {
  id: number;
  nombre: string;
  email: string;
  tipoConexion: string;
  servidor: string;
}

/* ── Tarjeta de perfil ── */
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
    >
      <div
        className="w-12 h-12 rounded-full flex items-center justify-center text-sm font-bold shadow-inner"
        style={{
          background: 'linear-gradient(135deg, #22D3EE, #06B6D4)',
          color: '#0F172A',
        }}
      >
        {getInitials(cuenta.nombre)}
      </div>
      <div className="text-center">
        <p className="text-xs font-bold truncate max-w-24" style={{ color: 'var(--color-text)' }}>
          {cuenta.nombre}
        </p>
        <p className="text-[10px] truncate max-w-24" style={{ color: 'var(--color-text-muted)' }}>
          {cuenta.email}
        </p>
      </div>
      <span className="text-[9px] px-2 py-0.5 rounded-full font-bold uppercase"
        style={{
          backgroundColor: 'rgba(34, 211, 238, 0.2)',
          color: '#22D3EE',
        }}>
        {cuenta.tipoConexion || 'IMAP'}
      </span>
    </div>
  );
}

function AddPerfilCard({ onClick }: { onClick: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 p-4 rounded-2xl border-2 border-dashed cursor-pointer transition-all duration-200 hover:scale-105 w-28 h-[128px]"
      style={{ borderColor: 'var(--color-border)', color: 'var(--color-text-muted)' }}
      onClick={onClick}>
      <span className="text-2xl leading-none">+</span>
      <span className="text-[10px] font-medium text-center">Añadir perfil</span>
    </div>
  );
}

export default function LoginPage() {
  const navigate = useNavigate();
  const { login, loginError, hayCuentas, loading: authLoading } = useAuth();
  const { mode, toggleMode } = useTheme();

  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState('');
  const [cuentas, setCuentas] = useState<Cuenta[]>([]);
  const [showSetupModal, setShowSetupModal] = useState(false);
  const [cuentaSeleccionada, setCuentaSeleccionada] = useState<number | null>(null);
  const [datosCargados, setDatosCargados] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    cuentaApi.list()
      .then(res => setCuentas(res.data || []))
      .catch(() => setCuentas([]))
      .finally(() => setDatosCargados(true));
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => inputRef.current?.focus(), 300);
    return () => clearTimeout(timer);
  }, [cuentaSeleccionada]);

  // Si es primera vez (sin cuentas), auto-abrir modal de configuración
  useEffect(() => {
    if (datosCargados && cuentas.length === 0) {
      setShowSetupModal(true);
    }
  }, [datosCargados, cuentas.length]);

  const handleSubmit = useCallback(async () => {
    if (!cuentaSeleccionada && cuentas.length > 0) {
      setStatus('Selecciona una cuenta primero');
      return;
    }
    if (!password) return;

    setLoading(true);
    setStatus('');

    const email = cuentaSeleccionada
      ? cuentas.find(c => c.id === cuentaSeleccionada)?.email || ''
      : '';

    try {
      const ok = await login(email, password);
      if (ok) {
        navigate('/', { replace: true });
      } else {
        setStatus(loginError || 'No se pudo iniciar sesión. Revisa tu conexión y la contraseña.');
      }
    } catch {
      setStatus('Error de conexión con el servidor');
    } finally {
      setLoading(false);
    }
  }, [cuentaSeleccionada, cuentas, password, login, navigate]);

  const handleAccountSaved = useCallback(async (result?: { email: string; password: string }) => {
    try {
      const res = await cuentaApi.list();
      setCuentas(res.data || []);
      // Si es la primera cuenta, auto-loguear con las credenciales recién creadas
      if (result?.email && result?.password) {
        setLoading(true);
        setStatus('Iniciando sesión...');
        const ok = await login(result.email, result.password);
        if (ok) {
          navigate('/', { replace: true });
          return;
        }
        setLoading(false);
        setStatus('Cuenta creada. Introduce tu contraseña para entrar.');
      }
    } catch { /* ignore */ }
    setShowSetupModal(false);
  }, [login, navigate]);

  const cuentaActiva = cuentas.find(c => c.id === cuentaSeleccionada);
  const mitad = Math.ceil(cuentas.length / 2);
  const izquierda = cuentas.slice(0, mitad);
  const derecha = cuentas.slice(mitad);

  const renderColumna = (lista: Cuenta[]) => (
    <div className="flex flex-col items-center gap-3">
      <p className="text-[11px] font-semibold uppercase tracking-widest mb-1"
        style={{ color: 'var(--color-text-muted)' }}>Perfiles</p>
      {lista.map(c => (
        <PerfilCard key={c.id} cuenta={c}
          seleccionada={c.id === cuentaSeleccionada}
          onSeleccionar={() => { setCuentaSeleccionada(c.id); setStatus(''); }} />
      ))}
      <AddPerfilCard onClick={() => setShowSetupModal(true)} />
    </div>
  );

  return (
    <div className="min-h-screen flex items-center justify-center relative overflow-hidden"
      style={{ backgroundColor: 'var(--color-bg)' }}>
      <div className="absolute inset-0 pointer-events-none login-gradient" />

      <div className="relative z-10 flex items-start justify-center gap-8 w-full max-w-5xl px-6">
        {cuentas.length > 0 && (
          <div className="w-28 shrink-0 pt-8">{renderColumna(izquierda)}</div>
        )}

        <div className="flex flex-col items-center gap-6 w-full max-w-sm">
          <div className="flex items-center justify-center">
            <img src="/logo.png" alt="eMail-IA" className="w-96 object-contain" />
          </div>

          {cuentaActiva ? (
            <div className="text-center space-y-1">
              <p className="text-xs" style={{ color: 'var(--color-text-muted)' }}>
                Cuenta seleccionada
              </p>
              <h1 className="text-base font-bold" style={{ color: 'var(--color-text)' }}>
                {cuentaActiva.email}
              </h1>
              <p className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
                Introduce tu contraseña de correo
              </p>
            </div>
          ) : (
            <div className="text-center space-y-1">
              <h1 className="text-lg font-bold" style={{ color: 'var(--color-text)' }}>
                {cuentas.length > 0 ? 'Selecciona un perfil' : '🆕 Configura tu cuenta'}
              </h1>
              <p className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>
                {cuentas.length > 0
                  ? 'Toca una cuenta de la izquierda/derecha e introduce tu contraseña IMAP'
                  : 'Añade tu primera cuenta de correo para empezar'}
              </p>
            </div>
          )}

          {cuentaActiva && (
            <input ref={inputRef} type="password" value={password}
              onChange={e => setPassword(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleSubmit()}
              placeholder="Contraseña de tu correo"
              className="w-full px-4 py-3 rounded-xl border text-sm outline-none transition-all duration-200
                placeholder:opacity-50 focus:ring-2 focus:ring-offset-2
                bg-[var(--color-bg)] text-[var(--color-text)]
                border-[var(--color-border)] focus:border-[var(--color-accent)] focus:ring-[var(--color-accent)]/30"
              autoComplete="off" disabled={loading} />
          )}

          {cuentaActiva && (
            <button onClick={handleSubmit} disabled={loading || !password}
              className="w-full py-3 rounded-xl font-bold text-sm transition-all duration-200
                disabled:opacity-40 disabled:cursor-not-allowed hover:brightness-110 active:scale-[0.98]
                text-white shadow-lg shadow-blue-500/20"
              style={{ background: 'linear-gradient(135deg, #3B82F6, #2563EB)' }}>
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
                  </svg>
                  Verificando IMAP...
                </span>
              ) : 'Entrar'}
            </button>
          )}

          {!cuentaActiva && cuentas.length === 0 && (
            <button onClick={() => setShowSetupModal(true)}
              className="w-full py-3 rounded-xl font-bold text-sm transition-all duration-200
                hover:brightness-110 active:scale-[0.98] text-white shadow-lg shadow-blue-500/20"
              style={{ background: 'linear-gradient(135deg, #3B82F6, #2563EB)' }}>
              Añadir cuenta de correo
            </button>
          )}

          {status && (
            <p className={`text-xs text-center font-medium ${
              status.includes('Error') || status.includes('incorrecta')
                ? 'text-red-500' : 'text-[var(--color-accent)]'
            }`}>
              {status}
            </p>
          )}

          <div className="flex items-center gap-2 py-2">
            <span className="text-[11px]" style={{ color: 'var(--color-text-muted)' }}>☾</span>
            <button onClick={toggleMode}
              className="w-9 h-5 rounded-full relative transition-colors flex items-center"
              style={{ backgroundColor: mode === 'dark' ? '#475569' : '#94A3B8' }}>
              <span className="w-3.5 h-3.5 rounded-full bg-white transition-all duration-200 mx-0.5"
                style={{ marginLeft: mode === 'dark' ? '0.125rem' : 'auto' }} />
            </button>
            <span className="text-[11px]" style={{ color: 'var(--color-text-muted)' }}>☼</span>
          </div>
        </div>

        {cuentas.length > 0 && (
          <div className="w-28 shrink-0 pt-8">{renderColumna(derecha)}</div>
        )}
      </div>

      <p className="absolute bottom-3 left-0 right-0 text-center text-[11px]"
        style={{ color: 'var(--color-text-muted)' }}>
        v{__APP_VERSION__}
      </p>

      <AccountSetupModal open={showSetupModal} onClose={handleAccountSaved} />
    </div>
  );
}
