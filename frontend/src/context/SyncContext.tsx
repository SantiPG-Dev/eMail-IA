import { createContext, useContext, useState, useCallback, useRef, useEffect, type ReactNode } from 'react';
import api, { cuentaApi, mensajeApi } from '../api/client';
import { conectarEventos, type SyncTerminadoEvento } from '../api/sse';

// Estado global de sincronización: sync manual al abrir la app, y push vía
// SSE (api/eventos): el backend avisa al terminar cualquier sync (manual o
// del scheduler de 5 min) → refreshKey++ y las vistas recargan solas.
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

function textoSync(ev: SyncTerminadoEvento): string {
  const base = `📬 ${ev.totalServer} (${ev.noLeidos} sin leer)`;
  return ev.descargados > 0
    ? `📨 +${ev.descargados} · ${base}`
    : `${base} · ${new Date().toLocaleTimeString()}`;
}

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
  // Refs de conexión SSE (evitan stale closures en callbacks estables)
  const sseConectadoRef = useRef(false);
  const sseVistoRef = useRef(false);

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

  // Sync manual (botón) y de arranque: 1ª cuenta. El refresco de la vista lo
  // hace el evento SSE sync-terminado que publica el backend; si el SSE está
  // caído, refrescamos aquí como fallback.
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

      if (!sseConectadoRef.current) await refreshMessages();
      setState(s => ({
        ...s, syncing: false,
        statusText: textoSync({ cuenta: c.email, descargados: totalDescargados, totalServer, noLeidos }),
        lastSync: new Date().toISOString(),
      }));
    } catch (e: any) {
      const errMsg = e?.response?.data?.error || e?.response?.data?.message || 'Error de conexión';
      setState(s => ({ ...s, syncing: false, statusText: `⚠️ ${errMsg}` }));
    } finally {
      syncingRef.current = false;
    }
  }, [refreshMessages]);

  // Suscripción SSE: aquí llega el aviso cuando el backend termina cualquier
  // sync (scheduler o manual, desde esta u otra vista) y también tras una
  // reconexión se refresca por si se perdieron eventos mientras caída.
  useEffect(() => {
    const desconectar = conectarEventos({
      onConexionCambiada: (conectado) => {
        const antes = sseConectadoRef.current;
        sseConectadoRef.current = conectado;
        // Catch-up solo en reconexiones (no en la 1ª conexión: init ya carga)
        if (conectado && sseVistoRef.current && !antes) refreshMessages();
        sseVistoRef.current = true;
      },
      onSyncTerminado: (ev) => {
        setState(s => ({ ...s, statusText: textoSync(ev), lastSync: new Date().toISOString() }));
        refreshMessages();
      },
    });
    return desconectar;
  }, [refreshMessages]);

  // Arranque: cargar estado local (BD) y sincronizar una vez. Sin polling:
  // las actualizaciones posteriores llegan por SSE.
  useEffect(() => {
    const init = async () => {
      try {
        const cuentas = await cuentaApi.list();
        if (cuentas.data.length > 0) {
          setState(s => ({ ...s, accountEmail: cuentas.data[0].email }));
          await refreshMessages();
          triggerSync();
        }
      } catch {
        // Backend aún no disponible (p.ej. arrancando): el SSE reintentará
      }
    };
    init();
  }, [refreshMessages, triggerSync]);

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
