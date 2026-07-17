import { createContext, useContext, useState, useCallback, useRef, useEffect, type ReactNode } from 'react';
import api, { cuentaApi, mensajeApi } from '../api/client';

// Estado global de sincronización: polling de backend, trigger manual, y
// refreshKey que se incrementa tras cada sync para que las vistas recarguen.
interface SyncState {
  syncing: boolean;
  progress: number;
  statusText: string;
  lastSync: string;
  totalMessages: number;
  accountEmail: string;
  currentLimite: number;
  refreshKey: number;  // Se incrementa tras cada sync para que las vistas recarguen
}

interface SyncContextType extends SyncState {
  triggerSync: () => Promise<void>;
  refreshMessages: () => Promise<number>;
}

const SyncContext = createContext<SyncContextType | null>(null);

const MAX = 50;
const STEP = 50;
const CUENTA_KEY = 'emailai_sync_limite';

export function SyncProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SyncState>({
    syncing: false,
    progress: 0,
    statusText: 'Esperando...',
    lastSync: '',
    totalMessages: 0,
    accountEmail: 'Sin cuenta',
    currentLimite: 0,
    refreshKey: 0,
  });
  const syncingRef = useRef(false);

  const refreshMessages = useCallback(async (): Promise<number> => {
    try {
      const cuentas = await cuentaApi.list();
      if (cuentas.data.length === 0) return 0;
      const c = cuentas.data[0];
      const res = await mensajeApi.list(c.email);
      const count = (res.data.mensajes || []).length;
      setState(s => ({ ...s, totalMessages: count, accountEmail: c.email, refreshKey: s.refreshKey + 1 }));
      return count;
    } catch { return 0; }
  }, []);

  const triggerSync = useCallback(async () => {
    if (syncingRef.current) return;
    syncingRef.current = true;
    setState(s => ({ ...s, syncing: true }));

    try {
      const cuentas = await cuentaApi.list();
      if (cuentas.data.length === 0) {
        setState(s => ({ ...s, syncing: false, statusText: 'Sin cuentas configuradas' }));
        syncingRef.current = false;
        return;
      }
      const c = cuentas.data[0];
      setState(s => ({ ...s, accountEmail: c.email }));

      const syncRes = await api.post(`/api/cuentas/${c.id}/sync?limite=50`);
      const resultados = syncRes.data || [];
      const totalDescargados = resultados.reduce((sum: number, r: any) => sum + (r.descargados || 0), 0);
      const totalServer = resultados.reduce((sum: number, r: any) => sum + (r.totalServer || 0), 0);
      const noLeidos = resultados.reduce((sum: number, r: any) => sum + (r.noLeidos || 0), 0);

      await refreshMessages();
      setState(s => ({
        ...s, syncing: false,
        statusText: `📨 +${totalDescargados} · 📬 ${totalServer} (${noLeidos} sin leer)`,
        lastSync: new Date().toISOString(),
      }));
    } catch (e: any) {
      const errMsg = e?.response?.data?.error || e?.response?.data?.message || 'Error de conexión';
      setState(s => ({ ...s, syncing: false, statusText: `⚠️ ${errMsg}` }));
    } finally {
      syncingRef.current = false;
    }
  }, [refreshMessages]);

  // Auto-sync silencioso cada 60s
  useEffect(() => {
    const syncStep = async () => {
      if (syncingRef.current) return;

      try {
        const cuentas = await cuentaApi.list();
        if (cuentas.data.length === 0) return;
        const c = cuentas.data[0];

        setState(s => ({ ...s, syncing: true, accountEmail: c.email }));

        try {
          const syncRes = await api.post(`/api/cuentas/${c.id}/sync?limite=${MAX}`);
          const resultados = syncRes.data || [];
          const totalDescargados = resultados.reduce((sum: number, r: any) => sum + (r.descargados || 0), 0);
          const totalServer = resultados.reduce((sum: number, r: any) => sum + (r.totalServer || 0), 0);
          const noLeidos = resultados.reduce((sum: number, r: any) => sum + (r.noLeidos || 0), 0);

          await refreshMessages();

          const ahora = new Date().toLocaleTimeString();
          setState(s => ({
            ...s, syncing: false, progress: 0,
            statusText: totalDescargados > 0
              ? `📨 +${totalDescargados} · 📬 ${totalServer} (${noLeidos} sin leer)`
              : `📬 ${totalServer} (${noLeidos} sin leer) · ${ahora}`,
            lastSync: new Date().toISOString(),
          }));
        } catch (e: any) {
          const errMsg = e?.response?.data?.message || e?.message || 'Error IMAP';
          setState(s => ({
            ...s, syncing: false, progress: 0,
            statusText: `⚠️ ${errMsg}`,
          }));
        }
      } catch {
        // Error inesperado
      }
    };

    // Cargar estado inicial
    const init = async () => {
      const cuentas = await cuentaApi.list();
      if (cuentas.data.length > 0) {
        setState(s => ({ ...s, accountEmail: cuentas.data[0].email }));
        await refreshMessages();
      }
      // Estado inicial según haya cuentas o no
      if (cuentas.data.length > 0) {
        setState(s => ({ ...s, statusText: `${s.totalMessages} mensajes` }));
      }
      syncStep();
    };
    init();

    const interval = setInterval(syncStep, 60000);
    return () => clearInterval(interval);
  }, [refreshMessages]);

  return (
    <SyncContext.Provider value={{ ...state, triggerSync, refreshMessages }}>
      {children}
    </SyncContext.Provider>
  );
}

export function useSync() {
  const ctx = useContext(SyncContext);
  if (!ctx) throw new Error('useSync must be used within SyncProvider');
  return ctx;
}
