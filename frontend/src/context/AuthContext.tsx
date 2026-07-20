import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import api from '../api/client';

interface AuthState {
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
}

interface AuthContextType extends AuthState {
  login: (email: string, password: string) => Promise<boolean>;
  logout: () => Promise<void>;
  cuentas: Array<{ id: number; email: string; nombre: string }>;
  hayCuentas: boolean;
  statusChecked: boolean;
  loginError: string | null;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(() => ({
    token: localStorage.getItem('emailai_token'),
    isAuthenticated: false,
    loading: true,
  }));
  const [cuentas, setCuentas] = useState<Array<{ id: number; email: string; nombre: string }>>([]);
  const [statusChecked, setStatusChecked] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);

  // Al arrancar: validar token guardado y cargar estado
  useEffect(() => {
    const init = async () => {
      try {
        const statusRes = await api.get('/api/auth/status');
        const configurada = statusRes.data?.configurada === true;
        const sesionActiva = statusRes.data?.sesionActiva === true;

        if (configurada) {
          const cuentasRes = await api.get('/api/cuentas');
          setCuentas(cuentasRes.data || []);
        }

        const token = localStorage.getItem('emailai_token');
        if (token && sesionActiva) {
          setState({ token, isAuthenticated: true, loading: false });
        } else if (token) {
          // Token guardado pero sesión expirada (backend reiniciado)
          localStorage.removeItem('emailai_token');
          setState({ token: null, isAuthenticated: false, loading: false });
        } else {
          setState({ token: null, isAuthenticated: false, loading: false });
        }
      } catch {
        setState({ token: null, isAuthenticated: false, loading: false });
      } finally {
        setStatusChecked(true);
      }
    };
    init();
  }, []);

  const login = useCallback(async (email: string, password: string): Promise<boolean> => {
    setState(s => ({ ...s, loading: true }));
    setLoginError(null);
    try {
      const res = await api.post('/api/auth/login', { email, masterPassword: password });
      const t = res.data.token;
      localStorage.setItem('emailai_token', t);
      setState({ token: t, isAuthenticated: true, loading: false });
      return true;
    } catch (err: any) {
      // Mostrar el mensaje real del backend (401 contraseña, 404 sin cuenta, 503 IMAP caído...)
      const msg = err?.response?.data?.error || err?.message || null;
      setLoginError(msg);
      setState(s => ({ ...s, loading: false }));
      return false;
    }
  }, []);

  const logout = useCallback(async () => {
    try { await api.post('/api/auth/logout'); } catch { /* ignore */ }
    localStorage.removeItem('emailai_token');
    setState({ token: null, isAuthenticated: false, loading: false });
  }, []);

  return (
    <AuthContext.Provider value={{
      ...state, login, logout,
      cuentas, hayCuentas: cuentas.length > 0,
      statusChecked, loginError,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
