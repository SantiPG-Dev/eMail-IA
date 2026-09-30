import { app } from "electron";

// Proxy app:// → backend + CSP del documento principal + validación de URLs
// (navegación y openExternal). Solo http/https salen al navegador del sistema.

// ── Validación de URLs (navegación y openExternal) ───────────────
// Solo http/https se delegan al navegador del sistema: shell.openExternal
// con esquemas arbitrarios (file://, smb://...) es una práctica prohibida.
export function esUrlNavegable(u: string): boolean {
  try {
    const p = new URL(u);
    return p.protocol === "http:" || p.protocol === "https:";
  } catch {
    return false;
  }
}

// Orígenes locales de confianza para ventanas hijas: la propia app (app://local),
// el callback OAuth (9876) y Vite dev (5173, solo sin empaquetar).
export function esOrigenLocalPermitido(u: string): boolean {
  try {
    const p = new URL(u);
    if (p.protocol === "app:" && p.host === "local") return true;
    const puertos = app.isPackaged ? ["9876"] : ["9876", "5173"];
    return (
      (p.hostname === "localhost" || p.hostname === "127.0.0.1") &&
      puertos.includes(p.port)
    );
  } catch {
    return false;
  }
}

// ── Proxy app:// → backend ────────────────────────────────────────
// Cada petición del renderer a app://local/<ruta> se reenvía al backend en
// 127.0.0.1:<puerto efímero>. Streaming (adjuntos), Authorization y
// Content-Disposition pasan tal cual.
const HOP_BY_HOP = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
  "host",
  // net.fetch ya decodifica la compresión: reenviarlos corrompería el cuerpo
  "content-length",
  "content-encoding",
]);

// ── Content-Security-Policy del documento principal ──────────────
// Antes solo existía la CSP per-correo (meta dentro del srcdoc); el documento
// principal iba sin CSP y una XSS en cualquier lib quedaba sin restricción
// (auditoría 2026-08-26). Ojo al diseño de img-src: el iframe del correo es
// srcdoc y HEREDA esta CSP — los correos LEGITIMO cargan imágenes http/https,
// y en no-LEGITIMO el meta img-src 'none' del iframe sigue mandando (las CSP
// se cruzan: gana la más estricta). Vite prod no genera scripts inline.
// frame-src incluye about: por el srcdoc; blob: por adjuntos embebidos.
export const CSP = {
  prod:
    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
    "img-src 'self' data: blob: http: https:; font-src 'self' data:; " +
    "connect-src 'self'; media-src 'self' blob:; frame-src 'self' blob: about:; " +
    "object-src 'none'; base-uri 'none'; frame-ancestors 'none'",
  // Dev con Vite (5173): React-refresh inyecta <script> inline y HMR necesita
  // ws: — relajación solo en desarrollo, nunca en empaquetado.
  dev:
    "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
    "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: http: https:; " +
    "font-src 'self' data:; connect-src 'self' ws:; media-src 'self' blob:; " +
    "frame-src 'self' blob: about:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'",
};

// El puerto lo aporta backend.ts en cada petición (efímero: se conoce al
// leer el ready file, después de registrar el protocol handler).
export function crearProxyBackend(
  puerto: () => number | null,
): (request: Request) => Promise<Response> {
  return async function proxyToBackend(request: Request): Promise<Response> {
    const backendPort = puerto();
    if (backendPort === null) {
      return new Response("Backend no disponible todavía", { status: 503 });
    }
    let u: URL;
    try {
      u = new URL(request.url);
    } catch {
      return new Response("URL malformada", { status: 400 });
    }
    const target = `http://127.0.0.1:${backendPort}${u.pathname}${u.search}`;
    const headers = new Headers();
    request.headers.forEach((value, key) => {
      const k = key.toLowerCase();
      // origin/referer del esquema custom app:// los rechaza el CorsFilter del
      // backend (403): el proxy es el boundary, al backend no le hace falta
      if (!HOP_BY_HOP.has(k) && k !== "origin" && k !== "referer")
        headers.set(key, value);
    });
    const init: RequestInit & { duplex?: "half" } = {
      method: request.method,
      headers,
    };
    if (request.method !== "GET" && request.method !== "HEAD") {
      init.body = request.body;
      init.duplex = "half";
    }
    try {
      // fetch de Node (undici), NO net.fetch: el network service de Chromium
      // rechaza (net::ERR_FAILED) peticiones salientes del protocol handler
      // con Referer/Origin del esquema custom app://
      const res = await fetch(target, init);
      const outHeaders = new Headers();
      res.headers.forEach((value, key) => {
        if (!HOP_BY_HOP.has(key.toLowerCase())) outHeaders.set(key, value);
      });
      // CSP del documento principal: todo lo que sirve el backend (SPA embebida
      // incluida) pasa por aquí en empaquetado. En respuestas no-HTML la ignora
      // el navegador, así que inyectarla siempre es seguro.
      if (!outHeaders.has("content-security-policy")) {
        outHeaders.set("Content-Security-Policy", CSP.prod);
      }
      console.log(
        `[Proxy] ${request.method} ${u.pathname} → ${res.status} ct=${res.headers.get("content-type") ?? "(none)"}`,
      );
      return new Response(res.body, {
        status: res.status,
        statusText: res.statusText,
        headers: outHeaders,
      });
    } catch (e) {
      console.error(
        `[Electron] Proxy ${request.method} ${u.pathname} → error:`,
        e,
      );
      return new Response("Backend no disponible", { status: 502 });
    }
  };
}
