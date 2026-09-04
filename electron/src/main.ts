import {
  app,
  BrowserWindow,
  shell,
  dialog,
  Tray,
  Menu,
  Notification,
  nativeImage,
  session,
  ipcMain,
  protocol,
} from "electron";
import * as path from "path";
import * as fs from "fs";
import * as os from "os";
import { spawn, execSync, type ChildProcess } from "child_process";
import * as http from "http";

// ── Config ──────────────────────────────────────────────────────
// Sin puertos fijos: el backend arranca con --server.port=0 (puerto efímero
// asignado por el SO en 127.0.0.1) y publica puerto+pid en un "ready file".
// El renderer no habla HTTP con el backend: carga app://local/ (protocolo
// propio de Electron) y el proceso main hace de proxy hacia 127.0.0.1:<efímero>.
// En dev, si Vite (5173) está corriendo, se usa Vite y el backend (8080) lo
// lanza el desarrollador aparte — el proxy de Vite ya apunta ahí.

process.env.ELECTRON_ENABLE_STACK_DUMPING = "false";
// Silenciar los logs internos de Chromium que ensucian la consola en desarrollo
// (ej. "[ERROR:debug_utils.cc] Hit debug scenario: 4" al cargar iframes srcdoc/about:blank).
// log-level=3 => solo mensajes FATAL. No afecta a la app.
app.commandLine.appendSwitch("log-level", "3");

const DEV_FRONTEND = "http://localhost:5173";
const APP_ORIGIN = "app://local";
let APP_URL = `${APP_ORIGIN}/`;
let backendPort: number | null = null;
const BACKEND_JAR = findJar();
const JAVA_BIN = findJava();
const READY_FILE = resolveReadyFile();

// El protocolo app:// es el origen del renderer: standard (URLs relativas),
// secure (localStorage, service workers), fetch/stream (API y adjuntos).
// Debe registrarse antes de app.ready().
protocol.registerSchemesAsPrivileged([
  {
    scheme: "app",
    privileges: {
      standard: true,
      secure: true,
      supportFetchAPI: true,
      stream: true,
      codeCache: true,
    },
  },
]);

// Ready file junto al jar (--jar=, instalación ~/.eMailAI), en userData
// (empaquetado) o en tmp (dev sin empaquetar). En dev se usa un subdirectorio
// privado 0700 por usuario (tmpdir es mundial-leíble: cualquier proceso local
// podría sembrar un ready file en la raíz y desviar el tráfico al backend).
function resolveReadyFile(): string {
  const jarArg = process.argv.find((a) => a.startsWith("--jar="));
  if (jarArg)
    return path.join(
      path.dirname(jarArg.slice("--jar=".length)),
      "backend.ready",
    );
  if (app.isPackaged)
    return path.join(app.getPath("userData"), "backend.ready");
  const dir = path.join(os.tmpdir(), `emailai-dev-${os.userInfo().uid}`);
  fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
  try {
    fs.chmodSync(dir, 0o700);
  } catch {
    /* Windows/FS sin chmod */
  }
  return path.join(dir, `backend-${process.pid}.ready`);
}

// ── Credenciales OAuth ──────────────────────────────────────────
// Se leen desde electron/oauth-config.json (no commiteado a git).
// Si el archivo no existe, se crea con un template vacío.
// Estas credenciales se pasan al backend como argumentos Spring.
// Ruta del oauth-config.json según contexto:
// - Dev: electron/oauth-config.json (junto al código, no commiteado a git).
// - Instalación con --jar=: junto al jar (~/.eMailAI/oauth-config.json, lo que
//   copia scripts/install.sh). El asar es de solo lectura, NUNCA ahí dentro.
// - Empaquetada sin --jar: userData (única ruta escribible garantizada).
function oauthConfigPath(): string {
  if (!app.isPackaged)
    return path.resolve(__dirname, "..", "oauth-config.json");
  const jarArg = process.argv.find((a) => a.startsWith("--jar="));
  if (jarArg)
    return path.join(
      path.dirname(jarArg.slice("--jar=".length)),
      "oauth-config.json",
    );
  return path.join(app.getPath("userData"), "oauth-config.json");
}

// Si el archivo no existe se crea un template vacío; si la ruta no es
// escribible se devuelve {} sin tumbar el arranque del backend.
function loadOAuthConfig(): Record<string, string> {
  const configPath = oauthConfigPath();
  const template = {
    google: { clientId: "", clientSecret: "" },
    microsoft: { clientId: "", clientSecret: "" },
  };

  if (!fs.existsSync(configPath)) {
    try {
      fs.writeFileSync(configPath, JSON.stringify(template, null, 2), "utf-8");
      // El template se rellena con el clientSecret de Google/Microsoft:
      // jamás legible por otros usuarios locales (umask 022 dejaría 0644)
      fs.chmodSync(configPath, 0o600);
      console.log(
        `[Electron] Creado ${configPath} (0600) — rellena tus credenciales OAuth`,
      );
    } catch (e) {
      console.warn(
        `[Electron] No se pudo crear ${configPath} (${(e as NodeJS.ErrnoException).code}); OAuth deshabilitado`,
      );
    }
    return {};
  }

  // Retro-corrección: versiones anteriores lo dejaban en 0644
  try {
    fs.chmodSync(configPath, 0o600);
  } catch {
    /* FS sin chmod */
  }

  try {
    const cfg = JSON.parse(fs.readFileSync(configPath, "utf-8"));
    const env: Record<string, string> = {};
    if (cfg.google?.clientId)
      env.EMAILAI_GOOGLE_CLIENT_ID = cfg.google.clientId;
    if (cfg.google?.clientSecret)
      env.EMAILAI_GOOGLE_CLIENT_SECRET = cfg.google.clientSecret;
    if (cfg.microsoft?.clientId)
      env.EMAILAI_MICROSOFT_CLIENT_ID = cfg.microsoft.clientId;
    if (cfg.microsoft?.clientSecret)
      env.EMAILAI_MICROSOFT_CLIENT_SECRET = cfg.microsoft.clientSecret;
    return env;
  } catch (e) {
    console.warn("[Electron] oauth-config.json inválido:", e);
    return {};
  }
}

// Dev: ¿está Vite corriendo? (5173). Si sí, la UI viene de Vite y el backend
// (8080) lo lanza el desarrollador aparte — el proxy de Vite ya apunta ahí.
function detectVite(): Promise<string | null> {
  if (app.isPackaged) return Promise.resolve(null);
  return new Promise((resolve) => {
    const req = http.get(DEV_FRONTEND, (res) => {
      res.resume();
      resolve(res.statusCode === 200 ? DEV_FRONTEND : null);
    });
    req.on("error", () => resolve(null));
    req.setTimeout(1000, () => {
      req.destroy();
      resolve(null);
    });
  });
}

// ── Validación de URLs (navegación y openExternal) ───────────────
// Solo http/https se delegan al navegador del sistema: shell.openExternal
// con esquemas arbitrarios (file://, smb://...) es una práctica prohibida.
function esUrlNavegable(u: string): boolean {
  try {
    const p = new URL(u);
    return p.protocol === "http:" || p.protocol === "https:";
  } catch {
    return false;
  }
}

// Orígenes locales de confianza para ventanas hijas: la propia app (app://local),
// el callback OAuth (9876) y Vite dev (5173, solo sin empaquetar).
function esOrigenLocalPermitido(u: string): boolean {
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

let mainWindow: BrowserWindow | null = null;
let backendProcess: ChildProcess | null = null;
let tray: Tray | null = null;

// ── Buscar el ejecutable de Java ────────────────────────────────
// 1) Argumento --java=
// 2) JRE empaquetado con jlink (resources/jre/bin/java[.exe]) — AppImage/deb/rpm/dmg/nsis
// 3) PATH del sistema (desarrollo)
function findJava(): string {
  const javaArg = process.argv.find((a) => a.startsWith("--java="));
  if (javaArg) return javaArg.slice("--java=".length);

  const javaBin = process.platform === "win32" ? "java.exe" : "java";
  const bundled = path.join(process.resourcesPath || "", "jre", "bin", javaBin);
  if (fs.existsSync(bundled)) return bundled;

  return "java";
}

// ── Buscar el JAR del backend ────────────────────────────────────
function findJar(): string | null {
  // 1) Argumento --jar
  const jarArg = process.argv.find((a) => a.startsWith("--jar="));
  if (jarArg) return jarArg.slice("--jar=".length);

  // 2) Desarrollo: JAR compilado en backend/target/
  const devJar = path.resolve(
    __dirname,
    "..",
    "..",
    "backend",
    "target",
    "emailai-backend-1.0.0.jar",
  );
  if (fs.existsSync(devJar)) return devJar;

  // 3) Producción: JAR en resources/
  const prodJar = path.join(process.resourcesPath || "", "backend.jar");
  if (fs.existsSync(prodJar)) return prodJar;

  return null; // Se usará mvn spring-boot:run
}

// ── Limpiar procesos anteriores ───────────────────────────────
function matarProcesosAnteriores() {
  // 1) Kill exacto por PID: el ready file stale del arranque anterior señala
  //    al JVM huérfano que mantiene el file lock de H2 (kill -9 del Electron
  //    deja al backend vivo). Cero riesgo de tocar procesos ajenos.
  const stale = readReadyFile();
  if (stale && pidAlive(stale.pid)) {
    try {
      process.kill(stale.pid, "SIGKILL");
      console.log(`[Electron] Backend anterior (pid ${stale.pid}) eliminado`);
    } catch (e) {
      console.warn(`[Electron] No se pudo matar el pid ${stale.pid}:`, e);
    }
  }

  // 2) Fallback por nombre: SOLO patrones exclusivos de esta app. Nunca
  //    'spring-boot:run' (mataría backends de otros proyectos del usuario).
  //    Los corchetes evitan que el patrón coincida con la propia cmdline del
  //    wrapper sh -c que ejecuta este pkill gotcha que ya mordió una vez:
  //    'emailai-backend-[0-9]' casa con "emailai-backend-1.2.0.jar" pero no
  //    consigo mismo; '[.]' exige un punto real en "com.emailai...".
  try {
    execSync(
      "pkill -9 -f 'emailai-backend-[0-9]' 2>/dev/null; " +
        "pkill -9 -f 'com[.]emailai[.]EmailAiApplication' 2>/dev/null; " +
        "true",
      { stdio: "ignore" },
    );
    console.log("[Electron] Barrido de procesos anteriores hecho");
  } catch {
    // Si no hay procesos, ignorar
  }
}

// ── Espera del ready file ─────────────────────────────────────────
// El backend escribe {"port":N,"pid":M} cuando Tomcat está listo. Poll corto
// del archivo (el evento solo se emite con el servidor YA sirviendo, no hace
// falta health check HTTP). El pid descarta archivos stale de un kill -9.
function readReadyFile(): { port: number; pid: number } | null {
  try {
    const raw = JSON.parse(fs.readFileSync(READY_FILE, "utf-8"));
    if (typeof raw.port === "number" && typeof raw.pid === "number") return raw;
  } catch {
    // Aún no existe o está a medio escribir
  }
  return null;
}

function pidAlive(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch (e) {
    return (e as NodeJS.ErrnoException).code === "EPERM";
  }
}

function waitForBackend(timeoutMs: number): Promise<number> {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const tick = () => {
      const info = readReadyFile();
      if (info && pidAlive(info.pid)) {
        resolve(info.port);
        return;
      }
      if (Date.now() > deadline) {
        reject(
          new Error(`Timeout esperando al backend (ready file: ${READY_FILE})`),
        );
        return;
      }
      setTimeout(tick, 150);
    };
    tick();
  });
}

// ── Proxy app:// → backend ────────────────────────────────────────
// Cada petición del renderer a app://local/<ruta> se reenvía al backend en
// 127.0.0.1:<puerto efímero>. Streaming (adjuntos), Authorization y
// Content-Disposition pasan tal cual.
const HOP_BY_HOP = new Set([
  'connection', 'keep-alive', 'proxy-authenticate', 'proxy-authorization',
  'te', 'trailer', 'transfer-encoding', 'upgrade', 'host',
  // net.fetch ya decodifica la compresión: reenviarlos corrompería el cuerpo
  'content-length', 'content-encoding',
]);

// ── Content-Security-Policy del documento principal ──────────────
// Antes solo existía la CSP per-correo (meta dentro del srcdoc); el documento
// principal iba sin CSP y una XSS en cualquier lib quedaba sin restricción
// (auditoría 2026-08-26). Ojo al diseño de img-src: el iframe del correo es
// srcdoc y HEREDA esta CSP — los correos LEGITIMO cargan imágenes http/https,
// y en no-LEGITIMO el meta img-src 'none' del iframe sigue mandando (las CSP
// se cruzan: gana la más estricta). Vite prod no genera scripts inline.
// frame-src incluye about: por el srcdoc; blob: por adjuntos embebidos.
const CSP = {
  prod: "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
      + "img-src 'self' data: blob: http: https:; font-src 'self' data:; "
      + "connect-src 'self'; media-src 'self' blob:; frame-src 'self' blob: about:; "
      + "object-src 'none'; base-uri 'none'; frame-ancestors 'none'",
  // Dev con Vite (5173): React-refresh inyecta <script> inline y HMR necesita
  // ws: — relajación solo en desarrollo, nunca en empaquetado.
  dev: "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
      + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: http: https:; "
      + "font-src 'self' data:; connect-src 'self' ws:; media-src 'self' blob:; "
      + "frame-src 'self' blob: about:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'",
};

async function proxyToBackend(request: Request): Promise<Response> {
  if (backendPort === null) {
    return new Response('Backend no disponible todavía', { status: 503 });
  }
  let u: URL;
  try {
    u = new URL(request.url);
  } catch {
    return new Response('URL malformada', { status: 400 });
  }
  const target = `http://127.0.0.1:${backendPort}${u.pathname}${u.search}`;
  const headers = new Headers();
  request.headers.forEach((value, key) => {
    const k = key.toLowerCase();
    // origin/referer del esquema custom app:// los rechaza el CorsFilter del
    // backend (403): el proxy es el boundary, al backend no le hace falta
    if (!HOP_BY_HOP.has(k) && k !== 'origin' && k !== 'referer') headers.set(key, value);
  });
  const init: RequestInit & { duplex?: 'half' } = { method: request.method, headers };
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    init.body = request.body;
    init.duplex = 'half';
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
    if (!outHeaders.has('content-security-policy')) {
      outHeaders.set('Content-Security-Policy', CSP.prod);
    }
    console.log(`[Proxy] ${request.method} ${u.pathname} → ${res.status} ct=${res.headers.get('content-type') ?? '(none)'}`);
    return new Response(res.body, { status: res.status, statusText: res.statusText, headers: outHeaders });
  } catch (e) {
    console.error(`[Electron] Proxy ${request.method} ${u.pathname} → error:`, e);
    return new Response('Backend no disponible', { status: 502 });
  }
}

// ── Spawn backend ────────────────────────────────────────────────
function ensureFrontendBuilt(): Promise<void> {
  return new Promise((resolve) => {
    // En instalación empaquetada (~/.eMailAI, --jar= o app empaquetada) el
    // frontend está EMBEBIDO en el jar (BOOT-INF/classes/static): no hay nada
    // que compilar. Solo tiene sentido construir en el checkout de desarrollo.
    const jarArg = process.argv.find(a => a.startsWith('--jar='));
    const frontendDir = path.resolve(__dirname, '..', '..', 'frontend');
    if (jarArg || app.isPackaged || !fs.existsSync(path.join(frontendDir, 'package.json'))) {
      console.log('[Electron] Frontend embebido en el jar — no se compila');
      resolve();
      return;
    }

    const indexPath = path.join(frontendDir, 'dist', 'index.html');
    if (fs.existsSync(indexPath)) { resolve(); return; }

    console.log('[Electron] Construyendo frontend React...');
    const pnpm = spawn('pnpm', ['build'], { cwd: frontendDir, stdio: 'pipe' });
    // Sin este handler, "pnpm" ausente lanza ENOENT no capturado y tumba la app
    pnpm.on('error', (err) => {
      console.warn(`[Electron] No se pudo lanzar pnpm (${err.message}); el backend usa su fallback`);
      resolve();
    });
    pnpm.on('close', (code) => {
      if (code === 0) console.log('[Electron] Frontend construido');
      else console.warn(`[Electron] Frontend build fallo (codigo ${code}), usando fallback`);
      resolve(); // Seguir aunque falle
    });
  });
}

function startBackend(): Promise<void> {
  return new Promise((resolve, reject) => {
    // Ready file de un kill -9 anterior: fuera antes de arrancar
    try {
      fs.rmSync(READY_FILE, { force: true });
    } catch {
      /* ignore */
    }

    if (BACKEND_JAR) {
      // data-dir: relativo ("DB") solo cuando un wrapper controla el cwd
      // (dev desde electron/ o instalación con --jar= desde ~/.eMailAI).
      // Empaquetado sin --jar (AppImage/deb/rpm) el cwd es el del lanzador
      // → ruta absoluta en userData para no regar la BD por ahí.
      const jarArg = process.argv.find((a) => a.startsWith("--jar="));
      const dataDir =
        !jarArg && app.isPackaged
          ? path.join(app.getPath("userData"), "DB")
          : "DB";
      console.log(
        `[Electron] Iniciando backend: ${JAVA_BIN} -jar ${BACKEND_JAR}`,
      );
      console.log(
        `[Electron] data-dir=${dataDir}, ready-file=${READY_FILE} (isPackaged=${app.isPackaged})`,
      );
      const oauthEnv = loadOAuthConfig();
      // heap capado; sin techo el JVM se come 25% de la RAM y arranca
      // con ~1,5% de heap inicial. EMAILAI_XMX sube el techo si Weka/H2 lo piden.
      const xmx = process.env.EMAILAI_XMX || "768m";
      backendProcess = spawn(
        JAVA_BIN,
        [
          "-Xms64m",
          `-Xmx${xmx}`,
          "-jar",
          BACKEND_JAR,
          "--server.port=0",
          `--emailai.data-dir=${dataDir}`,
          `--emailai.ready-file=${READY_FILE}`,
        ],
        {
          stdio: ["ignore", "pipe", "pipe"],
          env: { ...process.env, ...oauthEnv },
        },
      );
    } else {
      const backendDir = path.resolve(__dirname, "..", "..", "backend");
      console.log(
        `[Electron] Iniciando backend: mvn spring-boot:run en ${backendDir}`,
      );
      const oauthEnv = loadOAuthConfig();
      backendProcess = spawn("mvn", ["spring-boot:run"], {
        cwd: backendDir,
        stdio: ["ignore", "pipe", "pipe"],
        env: {
          ...process.env,
          ...oauthEnv,
          SERVER_PORT: "0",
          EMAILAI_READYFILE: READY_FILE,
        },
      });
    }

    backendProcess.stdout?.on("data", (data: Buffer) => {
      console.log(`[Backend] ${data.toString().trim()}`);
    });

    backendProcess.stderr?.on("data", (data: Buffer) => {
      console.error(`[Backend ERR] ${data.toString().trim()}`);
    });

    backendProcess.on("error", (err) => {
      console.error("[Electron] Error al iniciar backend:", err);
      const enoent = (err as NodeJS.ErrnoException).code === "ENOENT";
      reject(
        enoent
          ? new Error(
              `No se encontró "${JAVA_BIN}". Instala Java 21 o empaqueta el JRE (build-jre.sh).`,
            )
          : err,
      );
    });

    backendProcess.on("exit", (code) => {
      console.log(`[Electron] Backend terminado con código ${code}`);
      backendProcess = null;
    });

    // El backend publica puerto real en el ready file (server.port=0)
    waitForBackend(90_000)
      .then((port) => {
        backendPort = port;
        console.log(`[Electron] Backend listo en puerto efímero ${port}`);
        resolve();
      })
      .catch(reject);
  });
}

function stopBackend() {
  if (backendProcess) {
    console.log('[Electron] Deteniendo backend...');
    backendProcess.kill('SIGTERM');
    setTimeout(() => {
      if (backendProcess) {
        backendProcess.kill('SIGKILL');
        backendProcess = null;
      }
    }, 5000);
  }
}

// ── Splash screen ───────────────────────────────────────────────
function showSplash() {
  const splash = new BrowserWindow({
    width: 300,
    height: 300,
    frame: false,
    transparent: true,
    resizable: false,
    center: true,
    alwaysOnTop: true,
    icon: path.join(__dirname, '..', 'assets', 'icon-512.png'),
    webPreferences: { nodeIntegration: false, contextIsolation: true },
  });

  // Cargar splash HTML desde archivo
  splash.loadURL('file://' + path.resolve(__dirname, '..', 'splash.html') + '?v=' + app.getVersion());

  return splash;
}

// ── Ventana principal ────────────────────────────────────────────
async function createWindow() {
  await session.defaultSession.clearCache().catch(() => {});
  await session.defaultSession.clearStorageData({
    storages: ['localstorage', 'serviceworkers', 'cachestorage']
  }).catch(() => {});

  // Mostrar splash mientras carga
  const splash = showSplash();

  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    title: 'eMail-IA',
    icon: path.join(__dirname, '..', 'assets', 'icon-512.png'),
    show: false,
    backgroundColor: '#0F172A',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
    },
  });

  // Deshabilitar caché HTTP
  mainWindow.webContents.session.webRequest.onBeforeSendHeaders(
    { urls: ['*://*/*'] },
    (details: any, callback: any) => {
      callback({
        requestHeaders: {
          ...details.requestHeaders,
          'Cache-Control': 'no-cache, no-store, must-revalidate',
          'Pragma': 'no-cache',
        },
      });
    }
  );

  mainWindow.loadURL(APP_URL);

  // Visibilidad de errores del renderer en el log del main (diagnóstico E2E)
  mainWindow.webContents.on('did-fail-load', (_e, code, desc, url) =>
    console.error(`[Electron] did-fail-load ${code} ${desc} ${url}`));
  mainWindow.webContents.on('console-message', (_e, level, message) => {
    if (level >= 2) console.warn(`[Renderer] ${message}`);
  });

  // Esperar a que React termine y luego hacer transición
  mainWindow.webContents.on('did-finish-load', () => {
    setTimeout(async () => {
      // Animación: splash se escala, main aparece
      if (splash && !splash.isDestroyed()) {
        splash.close();
      }
      if (mainWindow && !mainWindow.isDestroyed() && !mainWindow.isVisible()) {
        mainWindow.show();
      }
    }, 800);
  });

  // Abrir enlaces externos en el navegador del sistema (OAuth).
  // Whitelist estricta: solo http/https llegan al navegador del sistema
  // (file://, smb://, javascript: etc. se bloquean); de localhost solo se
  // permiten ventanas hijas desde los puertos de la propia app — cualquier
  // otro proceso local podría servir una UI clónica que hereda el preload.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (esOrigenLocalPermitido(url)) {
      return { action: 'allow' };
    }
    if (esUrlNavegable(url)) {
      shell.openExternal(url);
    } else {
      console.warn(`[Electron] Ventana/openExternal bloqueado (URL no permitida): ${url}`);
    }
    return { action: 'deny' };
  });

  // La ventana principal solo navega dentro de la propia app (SPA); cualquier
  // navegación top-level a un origen ajeno se bloquea.
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (url !== APP_URL && !esOrigenLocalPermitido(url)) {
      console.warn(`[Electron] Navegación top-level bloqueada: ${url}`);
      event.preventDefault();
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  // Tray icon (64x64 para mejor visibilidad en HiDPI)
  try {
    let trayIconPath = path.join(__dirname, '..', 'assets', 'icon-128.png');
    if (!fs.existsSync(trayIconPath)) {
      trayIconPath = path.join(__dirname, '..', 'assets', 'icon-512.png');
    }
    if (fs.existsSync(trayIconPath)) {
      const trayIcon = nativeImage.createFromPath(trayIconPath);
      tray = new Tray(trayIcon);
      tray.setToolTip('eMail-IA');
      const contextMenu = Menu.buildFromTemplate([
        { label: 'Abrir eMail-IA', click: () => mainWindow?.show() },
        { type: 'separator' },
        { label: 'Salir', click: () => app.quit() },
      ]);
      tray.setContextMenu(contextMenu);
      tray.on('click', () => mainWindow?.show());
    }
  } catch (e) {
    console.log('[Electron] Tray no disponible:', e);
  }
}

// ── App lifecycle ────────────────────────────────────────────────
// ── IPC: purgar caché HTTP (anti-tracking al marcar SPAM) ─────────
ipcMain.handle('cache:clear', async () => {
  try { await session.defaultSession.clearCache(); } catch { /* ignore */ }
});

// ── IPC: enlaces externos con whitelist http/https ────────────────
// El preload expone openExternal para el flujo OAuth (URLs de Google/Microsoft);
// cualquier otro esquema se rechaza (shell.openExternal arbitrario = RCE vía xdg-open).
ipcMain.handle('shell:openExternal', async (_e, url: unknown) => {
  if (typeof url !== 'string' || !esUrlNavegable(url)) {
    throw new Error(`URL no permitida: ${String(url)}`);
  }
  await shell.openExternal(url);
});

// ── IPC: diálogos nativos de ficheros ─────────────────────────────
ipcMain.handle('dialog:openFile', (_e, options: Electron.OpenDialogOptions) =>
  dialog.showOpenDialog(options));
ipcMain.handle('dialog:saveFile', (_e, options: Electron.SaveDialogOptions) =>
  dialog.showSaveDialog(options));

// ── IPC: notificaciones nativas ───────────────────────────────────
// El preload sandboxed no puede instanciar Notification de electron.
ipcMain.handle('notification:show', (_e, title: unknown, body: unknown) => {
  if (typeof title !== 'string' || typeof body !== 'string') return;
  new Notification({ title, body }).show();
});

// ── Instancia única ──────────────────────────────────────────────
// Con puertos efímeros dos instancias NO chocan por red, pero sí por la BD H2
// (file lock). El segundo lanzamiento activa la ventana de la primera y muere.
const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      mainWindow.show();
      mainWindow.focus();
    }
  });

  app.whenReady().then(async () => {
    try {
      // El renderer vive en app://local/; el main lo proxya al backend
      protocol.handle('app', proxyToBackend);

      // Dev con Vite: UI desde 5173 y backend (8080) externo. Empaquetado o
      // dev sin Vite: backend hijo en puerto efímero + app://local/
      const viteUrl = await detectVite();
      if (viteUrl) {
        APP_URL = viteUrl;
        // La UI de Vite no pasa por el proxy app://: misma CSP pero relajada
        // para dev (scripts inline de react-refresh + ws: de HMR)
        session.defaultSession.webRequest.onHeadersReceived(
          { urls: [`${DEV_FRONTEND}/*`] },
          (details: any, callback: any) => {
            callback({
              responseHeaders: {
                ...details.responseHeaders,
                'Content-Security-Policy': [CSP.dev],
              },
            });
          }
        );
        console.log(`[Electron] Dev: UI en ${viteUrl} (backend externo en 8080 vía proxy Vite)`);
      } else {
        matarProcesosAnteriores();
        await ensureFrontendBuilt();
        await startBackend();
      }
      console.log(`[Electron] Abriendo ${APP_URL}`);
      createWindow();
    } catch (err) {
      console.error('[Electron] Error al iniciar:', err);
      dialog.showErrorBox('Error', `No se pudo iniciar el backend: ${err}`);
      app.quit();
    }
  });
}

app.on('window-all-closed', () => {
  stopBackend();
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('before-quit', () => {
  stopBackend();
});

app.on('activate', () => {
  if (mainWindow === null) {
    createWindow();
  }
});
