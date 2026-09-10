import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { ReactNode } from 'react';
import { SyncProvider, useSync } from '../context/SyncContext';
import type { EventosHandlers } from '../api/sse';

const mockPost = vi.fn();
const mockCuentaList = vi.fn();
const mockMensajeList = vi.fn();

vi.mock('../api/client', () => ({
  default: { post: (...args: any[]) => mockPost(...args) },
  cuentaApi: { list: () => mockCuentaList() },
  mensajeApi: { list: (...args: any[]) => mockMensajeList(...args) },
}));

// Capturamos los handlers que SyncContext registra al conectar: son la
// puerta para simular el evento SSE que envía el backend al terminar un sync.
let handlers: EventosHandlers;
vi.mock('../api/sse', () => ({
  conectarEventos: (h: EventosHandlers) => {
    handlers = h;
    return () => {};
  },
}));

const wrapper = ({ children }: { children: ReactNode }) => (
  <SyncProvider>{children}</SyncProvider>
);

describe('SyncContext — push SSE al terminar sync', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPost.mockResolvedValue({ data: [] });
    mockCuentaList.mockResolvedValue({ data: [{ id: 1, email: 'a@b.c' }] });
    mockMensajeList.mockResolvedValue({ data: { mensajes: [{ id: 1 }, { id: 2 }] } });
  });

  it('un sync-terminado del backend refresca (refreshKey++ y recarga de mensajes)', async () => {
    const { result } = renderHook(() => useSync(), { wrapper });

    // init: carga local + sync manual de arranque (con su recarga fallback)
    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/api/cuentas/1/sync?limite=50');
    });
    await waitFor(() => {
      expect(result.current.syncing).toBe(false);
    });
    const keyAntes = result.current.refreshKey;
    const cargasAntes = mockMensajeList.mock.calls.length;

    // El backend avisa por SSE de un sync del scheduler de 5 min
    await act(async () => {
      handlers.onSyncTerminado({
        cuenta: 'a@b.c', descargados: 3, totalServer: 10, noLeidos: 2,
      });
    });

    expect(result.current.refreshKey).toBeGreaterThan(keyAntes);
    expect(mockMensajeList.mock.calls.length).toBeGreaterThan(cargasAntes);
    expect(result.current.statusText).toContain('+3');
    expect(result.current.totalMessages).toBe(2);
  });
});
