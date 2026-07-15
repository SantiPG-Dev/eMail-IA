import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme, THEMES } from '../context/ThemeContext';
import { authApi, cuentaApi } from '../api/client';
import AccountSetupModal from '../components/AccountSetupModal';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login, setup, isAuthenticated } = useAuth();
  const { mode, toggleMode, setTheme, theme } = useTheme();

  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState('');
  const [needsSetup, setNeedsSetup] = useState(false);
  const [cuentas, setCuentas] = useState<any[]>([]);
  const [showSetupModal, setShowSetupModal] = useState(false);
  const loadedRef = useRef(false);

  // Solo ejecutar una vez al montar
  useEffect(() => {
    if (loadedRef.current) return;
    loadedRef.current = true;

    if (isAuthenticated) {
      navigate('/', { replace: true });
      return;
    }

    Promise.all([
      cuentaApi.list().catch(() => ({ data: [] })),
      authApi.status().catch(() => ({ data: { configurada: false } }))
    ]).then(([cuentasRes, statusRes]: [any, any]) => {
      const lista = cuentasRes.data || [];
      setCuentas(lista);
      const configurada = statusRes.data?.configurada === true;
      setNeedsSetup(!configurada);
      if (lista.length === 0 && !configurada) {
        setShowSetupModal(true);
      }
    });
  }, []); // Sin dependencias para que solo se ejecute UNA vez

  const handleSubmit = async () => {
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
  };

  const handleAccountSaved = async () => {
    try {
      const res = await cuentaApi.list();
      setCuentas(res.data || []);
    } catch {}
    setShowSetupModal(false);
  };

  return (
    <div className="flex min-h-screen" style={{ backgroundColor: 'var(--color-bg)' }}>
      <div className="flex-1 flex items-center justify-center p-5">
        <div className="flex gap-10 max-w-[900px] w-full">
          {/* Columna izquierda: tarjetas de cuentas */}
          <div className="w-[320px] shrink-0">
            {cuentas.length > 0 ? (
              <div className="space-y-3">
                <h2 className="text-sm font-bold mb-3" style={{ color: 'var(--color-text)' }}>
                  Tus cuentas
                </h2>
                {cuentas.map((c: any) => (
                  <div key={c.id}
                    className="rounded-xl p-4 border transition-colors"
                    style={{
                      backgroundColor: 'var(--color-bg-card)',
                      borderColor: 'var(--color-border)',
                    }}>
                    <div className="flex items-center gap-3 mb-2">
                      <span className="text-xl">📧</span>
                      <div>
                        <p className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>
                          {c.nombre}
                        </p>
                        <p className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
                          {c.email}
                        </p>
                      </div>
                    </div>
                    <div className="flex gap-2 mt-2">
                      <span className="text-[10px] px-2 py-0.5 rounded-full font-bold"
                        style={{
                          backgroundColor: c.tipoConexion === 'POP3' ? '#fbbf24' : '#22d3ee',
                          color: '#0F172A',
                        }}>
                        {c.tipoConexion || 'IMAP'}
                      </span>
                      <span className="text-[10px] px-2 py-0.5 rounded-full"
                        style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text-secondary)' }}>
                        {c.servidor || 'sin servidor'}
                      </span>
                    </div>
                  </div>
                ))}
                <button onClick={() => setShowSetupModal(true)}
                  className="w-full py-2 text-xs rounded-lg border-2 border-dashed transition-colors"
                  style={{
                    borderColor: 'var(--color-border)',
                    color: 'var(--color-text-secondary)',
                  }}>
                  + Añadir otra cuenta
                </button>
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center h-full text-center">
                <h2 className="text-sm font-bold mb-2" style={{ color: 'var(--color-text)' }}>
                  Bienvenido a eMail-IA
                </h2>
                <p className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
                  Configura tu primera cuenta de correo para empezar
                </p>
                <button onClick={() => setShowSetupModal(true)}
                  className="mt-4 px-4 py-2 text-sm font-bold rounded-pill"
                  style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>
                  Configurar cuenta
                </button>
              </div>
            )}
          </div>

          {/* Separador flexible */}
          <div className="flex-1" />

          {/* Columna derecha: logo + contraseña */}
          <div className="w-[520px] flex flex-col items-center gap-5">
            <div className="w-[480px] h-[220px] rounded-xl flex items-center justify-center"
              style={{ backgroundColor: 'var(--color-bg-card)' }}>
              <img src="/logo.png" alt="eMail-IA"
                className="max-w-full max-h-full object-contain" />
            </div>

            <div className="flex flex-col items-center max-w-[360px] w-full gap-2">
              {needsSetup ? (
                <>
                  <h3 className="text-sm font-bold" style={{ color: 'var(--color-accent)' }}>
                    🆕 Primer acceso
                  </h3>
                  <p className="text-xs text-center" style={{ color: 'var(--color-text-secondary)' }}>
                    Crea una contraseña maestra para proteger tus datos
                  </p>
                </>
              ) : (
                <h3 className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>
                  🔐 Introduce la contraseña
                </h3>
              )}

              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleSubmit()}
                placeholder={needsSetup ? 'Crea una contraseña maestra' : 'Introduce la contraseña maestra'}
                className="w-full px-3 py-2 rounded-lg border text-sm outline-none transition-colors"
                style={{
                  backgroundColor: 'var(--color-bg)',
                  color: 'var(--color-text)',
                  borderColor: 'var(--color-border)',
                }}
                autoFocus
              />

              <button
                onClick={handleSubmit}
                disabled={loading || !password}
                className="w-full py-2.5 rounded-pill font-bold text-sm transition-opacity disabled:opacity-50"
                style={{
                  backgroundColor: 'var(--color-accent)',
                  color: '#0F172A',
                }}
              >
                {loading ? 'Procesando...' : needsSetup ? 'Configurar y entrar' : 'Entrar'}
              </button>

              {status && (
                <p className="text-xs" style={{ color: status.includes('Error') ? '#ef4444' : 'var(--color-accent)' }}>
                  {status}
                </p>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Selector de tema */}
      <div className="absolute bottom-0 left-0 right-0 flex items-center justify-center gap-3 py-2.5">
        <span className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>☾</span>
        <button onClick={toggleMode}
          className="w-10 h-5 rounded-full relative transition-colors"
          style={{ backgroundColor: mode === 'dark' ? '#475569' : '#64748B' }}>
          <span className="absolute w-4 h-4 rounded-full bg-white top-0.5 transition-transform"
            style={{ left: mode === 'dark' ? '3px' : '22px' }} />
        </button>
        <span className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>☼</span>
        <select value={theme} onChange={e => setTheme(e.target.value as any)}
          className="ml-4 text-xs px-2 py-1 rounded border"
          style={{
            backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text)',
            borderColor: 'var(--color-border)',
          }}>
          {THEMES.map(t => <option key={t} value={t}>{t}</option>)}
        </select>
      </div>

      <AccountSetupModal open={showSetupModal} onClose={handleAccountSaved} />
    </div>
  );
}
