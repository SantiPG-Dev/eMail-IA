import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme, THEMES } from '../context/ThemeContext';
import { authApi } from '../api/client';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login, setup, isAuthenticated } = useAuth();
  const { mode, toggleMode, setTheme, theme } = useTheme();

  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState('');
  const [needsSetup, setNeedsSetup] = useState(false);

  useEffect(() => {
    if (isAuthenticated) navigate('/', { replace: true });
    // Verificar si ya hay contraseña configurada
    authApi.status().then(res => {
      if (!res.data.configurada) setNeedsSetup(true);
    }).catch(() => setNeedsSetup(true));
  }, [isAuthenticated, navigate]);

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

  return (
    <div className="flex flex-col min-h-screen" style={{ backgroundColor: 'var(--color-bg)' }}>
      <div className="flex-1 flex items-center justify-center p-5">
        <div className="flex gap-10 max-w-[900px] w-full">
          {/* Columna izquierda: información / cuentas guardadas */}
          <div className="w-[280px] shrink-0">
            <h2 className="text-sm font-bold mb-2.5" style={{ color: 'var(--color-text)' }}>
              {needsSetup ? 'Configuración inicial' : 'Iniciar sesión'}
            </h2>
            <p className="text-xs mb-4" style={{ color: 'var(--color-text-secondary)' }}>
              {needsSetup
                ? 'Esta es la primera ejecución. Crea una contraseña maestra para proteger tus datos.'
                : 'Introduce tu contraseña maestra para acceder a la aplicación.'}
            </p>
          </div>

          {/* Separador flexible */}
          <div className="flex-1" />

          {/* Columna derecha: logo + contraseña */}
          <div className="w-[520px] flex flex-col items-center gap-5">
            {/* Logo */}
            <img src="/logo.png" alt="eMail-IA"
              className="w-[480px] h-[220px] object-contain rounded-xl" />

            {/* Bloque contraseña */}
            <div className="flex flex-col items-center max-w-[360px] w-full gap-2">
              <h3 className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>
                Contraseña de la aplicación
              </h3>

              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleSubmit()}
                placeholder="Introduce la contraseña maestra"
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
                <p className="text-xs" style={{ color: 'var(--color-accent)' }}>
                  {status}
                </p>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Selector de tema */}
      <div className="flex items-center justify-center gap-3 py-2.5">
        <span className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>☾</span>
        <button
          onClick={toggleMode}
          className="w-10 h-5 rounded-full relative transition-colors"
          style={{
            backgroundColor: mode === 'dark' ? '#475569' : '#64748B',
          }}
        >
          <span
            className="absolute w-4 h-4 rounded-full bg-white top-0.5 transition-transform"
            style={{ left: mode === 'dark' ? '3px' : '22px' }}
          />
        </button>
        <span className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>☼</span>
        {/* Selector de palette */}
        <select
          value={theme}
          onChange={e => setTheme(e.target.value as any)}
          className="ml-4 text-xs px-2 py-1 rounded border"
          style={{
            backgroundColor: 'var(--color-bg-card)',
            color: 'var(--color-text)',
            borderColor: 'var(--color-border)',
          }}
        >
          {THEMES.map(t => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>
      </div>
    </div>
  );
}
