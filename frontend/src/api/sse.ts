// Cliente SSE basado en fetch. No usamos EventSource nativo porque no
// permite mandar headers (Authorization con el JWT) y no queremos exponer
// el token en query params. Funciona igual en dev (proxy de Vite) que en
// producción (proxy app://local de Electron), ambos pasan el stream tal cual.

export interface SyncTerminadoEvento {
  cuenta: string;
  descargados: number;
  totalServer: number;
  noLeidos: number;
}

export interface EventosHandlers {
  onSyncTerminado: (ev: SyncTerminadoEvento) => void;
  /** Cambios de estado de la conexión (true = conectado). */
  onConexionCambiada?: (conectado: boolean) => void;
}

const TOKEN_KEY = 'emailai_token';
const REINTENTO_INICIAL_MS = 1000;
const REINTENTO_MAX_MS = 30000;

// Abre el stream /api/eventos y lo mantiene vivo: reconexión con backoff
// exponencial (1s→30s) si cae, parada limpia al desmontar o si hay 401
// (sin sesión, las llamadas axios redirigen a login; aquí solo paramos).
export function conectarEventos(handlers: EventosHandlers): () => void {
  const controller = new AbortController();
  let cerrado = false;

  const bucle = async () => {
    let reintento = 0;
    while (!cerrado) {
      let conectado = false;
      try {
        const token = localStorage.getItem(TOKEN_KEY);
        const res = await fetch('/api/eventos', {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
          signal: controller.signal,
          cache: 'no-store',
        });
        if (res.status === 401) {
          // Token inválido/caducado: no tiene sentido reintentar aquí
          return;
        }
        if (!res.ok || !res.body) {
          throw new Error(`SSE HTTP ${res.status}`);
        }
        conectado = true;
        reintento = 0;
        handlers.onConexionCambiada?.(true);
        await leerStream(res.body, handlers);
        // Stream cerrado por el servidor → reconectar abajo
      } catch {
        // Abort (desmontaje) o error de red: se decide abajo
      } finally {
        if (conectado) handlers.onConexionCambiada?.(false);
      }
      if (cerrado || controller.signal.aborted) return;
      const espera = Math.min(REINTENTO_MAX_MS, REINTENTO_INICIAL_MS * 2 ** reintento);
      reintento++;
      await new Promise(r => setTimeout(r, espera));
    }
  };

  bucle();

  return () => {
    cerrado = true;
    controller.abort();
  };
}

// Parsea frames SSE ("event:...\ndata:...\n\n") del ReadableStream.
async function leerStream(body: ReadableStream<Uint8Array>, handlers: EventosHandlers): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) return;
      buffer += decoder.decode(value, { stream: true });
      let fin: number;
      while ((fin = buffer.indexOf('\n\n')) !== -1) {
        const frame = buffer.slice(0, fin);
        buffer = buffer.slice(fin + 2);
        procesarFrame(frame, handlers);
      }
    }
  } finally {
    reader.releaseLock();
  }
}

function procesarFrame(frame: string, handlers: EventosHandlers): void {
  let evento = 'message';
  const dataLines: string[] = [];
  for (const linea of frame.split('\n')) {
    if (linea.startsWith('event:')) {
      evento = linea.slice(6).trim();
    } else if (linea.startsWith('data:')) {
      dataLines.push(linea.slice(5).trim());
    }
  }
  if (evento !== 'sync-terminado' || dataLines.length === 0) return; // heartbeat/conexion se ignoran
  try {
    handlers.onSyncTerminado(JSON.parse(dataLines.join('\n')));
  } catch {
    // JSON malformado: ignorar (el heartbeat del backend mantiene el flujo)
  }
}
