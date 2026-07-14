import { createContext, useContext, useState, useCallback, useRef, useEffect, type ReactNode } from 'react';
import api, { cuentaApi, mensajeApi } from '../api/client';

interface SyncState {
  syncing: boolean;
  progress: number;
  statusText: string;
  lastSync: string;
  totalMessages: number;
  accountEmail: string;
  currentLimite: number;
  refreshKey: number;  // Se incrementa tras cada sync, para que las vistas sepan recargar
}

interface SyncContextType extends SyncState {
  triggerSync: () => Promise<void>;
  refreshMessages: () => Promise<number>;
}

const SyncContext = createContext<SyncContextType | null>(null);

const MAX = 300;
const STEP = 25;
const CUENTA_KEY = 'emailai_sync_limite';

export function SyncProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SyncState>({
    syncing: false,
    progress: 0,
    statusText: 'Inactivo',
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
    if (syncingRef.current) {
      setState(s => ({ ...s, statusText: 'Ya sincronizando...' }));
      return;
    }
    syncingRef.current = true;
    setState(s => ({ ...s, syncing: true, progress: 10, statusText: 'Sincronizando...' }));

    try {
      const cuentas = await cuentaApi.list();
      if (cuentas.data.length === 0) {
        setState(s => ({ ...s, syncing: false, statusText: 'Sin cuentas configuradas' }));
        syncingRef.current = false;
        return;
      }
      const c = cuentas.data[0];
      setState(s => ({ ...s, progress: 30, statusText: `Descargando correos (${c.email})...`, accountEmail: c.email }));

      await api.post(`/api/cuentas/${c.id}/sync?limite=300`);
      localStorage.setItem(CUENTA_KEY, '300');

      setState(s => ({ ...s, progress: 70, statusText: 'Actualizando bandeja...' }));
      const total = await refreshMessages();

      setState(s => ({
        ...s, syncing: false, progress: 100,
        statusText: `${total} mensajes · ${new Date().toLocaleTimeString()}`,
        lastSync: new Date().toISOString(),
        currentLimite: 300,
      }));
      setTimeout(() => setState(s => ({ ...s, progress: 0 })), 3000);
    } catch {
      setState(s => ({ ...s, syncing: false, statusText: 'Error de conexión' }));
    } finally {
      syncingRef.current = false;
    }
  }, [refreshMessages]);

  // Auto-sync progresivo cada 60s
  useEffect(() => {
    const syncStep = async () => {
      if (syncingRef.current) return;

      try {
        const cuentas = await cuentaApi.list();
        if (cuentas.data.length === 0) return;
        const c = cuentas.data[0];

        let limite = parseInt(localStorage.getItem(CUENTA_KEY) || '0');
        if (limite <= 0) limite = STEP;
        else if (limite < MAX) limite = Math.min(limite + STEP, MAX);

        localStorage.setItem(CUENTA_KEY, String(limite));

        setState(s => ({
          ...s, syncing: true, progress: Math.round((limite / MAX) * 50),
          statusText: `Sincronizando ${limite}/${MAX}...`,
          accountEmail: c.email, currentLimite: limite,
        }));

        await api.post(`/api/cuentas/${c.id}/sync?limite=${limite}`);
        await refreshMessages();

        const actualMsg = (await refreshMessages()) || 0;
        const pct = limite >= MAX ? 100 : Math.round((limite / MAX) * 100);
        if (actualMsg > 0) {
          setState(s => ({
            ...s, syncing: false, progress: pct,
            statusText: limite >= MAX
              ? `${actualMsg} mensajes · ${new Date().toLocaleTimeString()}`
              : `${limite}/${MAX} · ${actualMsg} descargados`,
            lastSync: new Date().toISOString(),
          }));
        }
        if (limite >= MAX) {
          setTimeout(() => setState(s => ({ ...s, progress: 0 })), 3000);
        }
      } catch (e: any) {
        const errMsg = e?.response?.data?.message || e?.message || 'Error de conexión IMAP';
        setState(s => ({
          ...s, syncing: false, progress: 0,
          statusText: `⚠️ ${errMsg}`,
        }));
        setTimeout(() => setState(s => ({ ...s, statusText: 'Error IMAP. Revisa credenciales.' })), 5000);
      }
    };

    // Cargar estado inicial
    const init = async () => {
      const cuentas = await cuentaApi.list();
      if (cuentas.data.length > 0) {
        setState(s => ({ ...s, accountEmail: cuentas.data[0].email }));
        await refreshMessages();
        const limite = parseInt(localStorage.getItem(CUENTA_KEY) || '0');
        setState(s => ({ ...s, currentLimite: limite || STEP }));
      }
      syncStep(); // Primer sync al cargar
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
