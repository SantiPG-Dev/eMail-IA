import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import api from '../api/client';

interface AuthState {
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
}

interface AuthContextType extends AuthState {
  login: (password: string) => Promise<boolean>;
  logout: () => Promise<void>;
  setup: (password: string) => Promise<boolean>;
  isConfigured: boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(() => ({
    token: localStorage.getItem('emailai_token'),
    isAuthenticated: false,
    loading: true,  // Arranca validando el token guardado
  }));
  const [isConfigured, setIsConfigured] = useState(false);

  // Al arrancar: valida el token contra el backend o lo borra si es inválido
  useEffect(() => {
    const token = localStorage.getItem('emailai_token');
    if (!token) {
      setState({ token: null, isAuthenticated: false, loading: false });
      return;
    }

    api.get('/api/cuentas')
      .then(() => {
        setState({ token, isAuthenticated: true, loading: false });
      })
      .catch(() => {
        localStorage.removeItem('emailai_token');
        setState({ token: null, isAuthenticated: false, loading: false });
      });
  }, []);

  const login = useCallback(async (password: string): Promise<boolean> => {
    setState(s => ({ ...s, loading: true }));
    try {
      const res = await api.post('/api/auth/login', { masterPassword: password });
      const t = res.data.token;
      localStorage.setItem('emailai_token', t);
      setState({ token: t, isAuthenticated: true, loading: false });
      return true;
    } catch {
      setState(s => ({ ...s, loading: false }));
      return false;
    }
  }, []);

  const logout = useCallback(async () => {
    try { await api.post('/api/auth/logout'); } catch { /* ignore */ }
    localStorage.removeItem('emailai_token');
    setState({ token: null, isAuthenticated: false, loading: false });
  }, []);

  const setup = useCallback(async (password: string): Promise<boolean> => {
    try {
      await api.post('/api/auth/setup', { masterPassword: password });
      setIsConfigured(true);
      return true;
    } catch {
      return false;
    }
  }, []);

  return (
    <AuthContext.Provider value={{ ...state, login, logout, setup, isConfigured }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
