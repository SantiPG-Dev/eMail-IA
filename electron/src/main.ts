import { app, BrowserWindow, shell, dialog, Tray, Menu, Notification, nativeImage, session, ipcMain } from 'electron';
import * as path from 'path';
import * as fs from 'fs';
import { spawn, execSync, ChildProcess } from 'child_process';
import * as http from 'http';

// ── Config ──────────────────────────────────────────────────────
// El backend Spring Boot se ejecuta como proceso hijo en el puerto 8420.
// Electron detecta si Vite dev está corriendo (puerto 5173) y usa esa URL,
// o si no, sirve el build estático desde el backend embeebido.

process.env.ELECTRON_ENABLE_STACK_DUMPING = 'false';
// Silenciar los logs internos de Chromium que ensucian la consola en desarrollo
// (ej. "[ERROR:debug_utils.cc] Hit debug scenario: 4" al cargar iframes srcdoc/about:blank).
// log-level=3 => solo mensajes FATAL. No afecta a la app.
app.commandLine.appendSwitch('log-level', '3');

const BACKEND_PORT = 8420;
const HEALTH_URL = `http://localhost:${BACKEND_PORT}/health`;
const DEV_FRONTEND = 'http://localhost:5173';
let APP_URL = `http://localhost:${BACKEND_PORT}`;
const BACKEND_JAR = findJar();
const JAVA_BIN = findJava();

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
  if (!app.isPackaged) return path.resolve(__dirname, '..', 'oauth-config.json');
  const jarArg = process.argv.find(a => a.startsWith('--jar='));
  if (jarArg) return path.join(path.dirname(jarArg.slice('--jar='.length)), 'oauth-config.json');
  return path.join(app.getPath('userData'), 'oauth-config.json');
}

// Si el archivo no existe se crea un template vacío; si la ruta no es
// escribible se devuelve {} sin tumbar el arranque del backend.
function loadOAuthConfig(): Record<string, string> {
  const configPath = oauthConfigPath();
  const template = {
    google: { clientId: '', clientSecret: '' },
    microsoft: { clientId: '', clientSecret: '' }
  };

  if (!fs.existsSync(configPath)) {
    try {
      fs.writeFileSync(configPath, JSON.stringify(template, null, 2), 'utf-8');
      console.log(`[Electron] Creado ${configPath} — rellena tus credenciales OAuth`);
    } catch (e) {
      console.warn(`[Electron] No se pudo crear ${configPath} (${(e as NodeJS.ErrnoException).code}); OAuth deshabilitado`);
    }
    return {};
  }

  try {
    const cfg = JSON.parse(fs.readFileSync(configPath, 'utf-8'));
    const env: Record<string, string> = {};
    if (cfg.google?.clientId) env.EMAILAI_GOOGLE_CLIENT_ID = cfg.google.clientId;
    if (cfg.google?.clientSecret) env.EMAILAI_GOOGLE_CLIENT_SECRET = cfg.google.clientSecret;
    if (cfg.microsoft?.clientId) env.EMAILAI_MICROSOFT_CLIENT_ID = cfg.microsoft.clientId;
    if (cfg.microsoft?.clientSecret) env.EMAILAI_MICROSOFT_CLIENT_SECRET = cfg.microsoft.clientSecret;
    return env;
  } catch (e) {
    console.warn('[Electron] oauth-config.json inválido:', e);
    return {};
  }
}

function detectFrontendUrl(): Promise<string> {
  return new Promise((resolve) => {
    const req = http.get(DEV_FRONTEND, (res) => {
      res.resume();
      resolve(res.statusCode === 200 ? DEV_FRONTEND : `http://localhost:${BACKEND_PORT}`);
    });
    req.on('error', () => resolve(`http://localhost:${BACKEND_PORT}`));
    req.setTimeout(1000, () => { req.destroy(); resolve(`http://localhost:${BACKEND_PORT}`); });
  });
}

// ── Validación de URLs (navegación y openExternal) ───────────────
// Solo http/https se delegan al navegador del sistema: shell.openExternal
// con esquemas arbitrarios (file://, smb://...) es una práctica prohibida.
function esUrlNavegable(u: string): boolean {
  try {
    const p = new URL(u);
    return p.protocol === 'http:' || p.protocol === 'https:';
  } catch {
    return false;
  }
}

// Orígenes locales de confianza para ventanas hijas: la propia app (8420),
// el callback OAuth (9876) y Vite dev (5173, solo sin empaquetar).
function esOrigenLocalPermitido(u: string): boolean {
  try {
    const p = new URL(u);
    const puertos = app.isPackaged ? ['8420', '9876'] : ['8420', '9876', '5173'];
    return p.hostname === 'localhost' && puertos.includes(p.port);
  } catch {
    return false;
  }
}

let mainWindow: BrowserWindow | null = null;
let backendProcess: ChildProcess | null = null;
let tray: Tray | null = null;

// ── Buscar el ejecutable de Java ────────────────────────────────
// 1) Argumento --java=
// 2) JRE empaquetado con jlink (resources/jre/bin/java) — AppImage/deb/rpm
// 3) PATH del sistema (desarrollo)
function findJava(): string {
  const javaArg = process.argv.find(a => a.startsWith('--java='));
  if (javaArg) return javaArg.slice('--java='.length);

  if (process.platform !== 'win32') {
    const bundled = path.join(process.resourcesPath || '', 'jre', 'bin', 'java');
    if (fs.existsSync(bundled)) return bundled;
  }

  return 'java';
}

// ── Buscar el JAR del backend ────────────────────────────────────
function findJar(): string | null {
  // 1) Argumento --jar
  const jarArg = process.argv.find(a => a.startsWith('--jar='));
  if (jarArg) return jarArg.slice('--jar='.length);

  // 2) Desarrollo: JAR compilado en backend/target/
  const devJar = path.resolve(__dirname, '..', '..', 'backend', 'target', 'emailai-backend-1.0.0.jar');
  if (fs.existsSync(devJar)) return devJar;

  // 3) Producción: JAR en resources/
  const prodJar = path.join(process.resourcesPath || '', 'backend.jar');
  if (fs.existsSync(prodJar)) return prodJar;

  return null; // Se usará mvn spring-boot:run
}

// ── Limpiar procesos anteriores ───────────────────────────────
function matarProcesosAnteriores() {
  try {
    const dbPath = path.resolve(__dirname, '..', '..', 'backend', 'DB', 'emailai.mv.db');
    // Matar procesos que tienen el archivo de BD abierto
    execSync(`fuser -k ${dbPath} 2>/dev/null; true`, { stdio: 'ignore' });
    // Matar procesos java/maven del backend
    execSync(
      "pkill -9 -f 'spring-boot:run' 2>/dev/null; " +
      "pkill -9 -f 'EmailAiApplication' 2>/dev/null; " +
      "pkill -9 -f 'emailai-backend' 2>/dev/null; " +
      "true",
      { stdio: 'ignore' }
    );
    console.log('[Electron] Procesos anteriores eliminados');
  } catch {
    // Si no hay procesos, ignorar
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
    if (BACKEND_JAR) {
      // data-dir: relativo ("DB") solo cuando un wrapper controla el cwd
      // (dev desde electron/ o instalación con --jar= desde ~/.eMailAI).
      // Empaquetado sin --jar (AppImage/deb/rpm) el cwd es el del lanzador
      // → ruta absoluta en userData para no regar la BD por ahí.
      const jarArg = process.argv.find(a => a.startsWith('--jar='));
      const dataDir = (!jarArg && app.isPackaged)
        ? path.join(app.getPath('userData'), 'DB')
        : 'DB';
      console.log(`[Electron] Iniciando backend: ${JAVA_BIN} -jar ${BACKEND_JAR}`);
      console.log(`[Electron] data-dir=${dataDir} (isPackaged=${app.isPackaged}, cwd=${process.cwd()})`);
      const oauthEnv = loadOAuthConfig();
      backendProcess = spawn(JAVA_BIN, [
        '-jar', BACKEND_JAR,
        `--server.port=${BACKEND_PORT}`,
        `--emailai.data-dir=${dataDir}`,
      ], {
        stdio: ['ignore', 'pipe', 'pipe'],
        env: { ...process.env, ...oauthEnv },
      });
    } else {
      const backendDir = path.resolve(__dirname, '..', '..', 'backend');
      console.log(`[Electron] Iniciando backend: mvn spring-boot:run en ${backendDir}`);
      const oauthEnv = loadOAuthConfig();
      backendProcess = spawn('mvn', ['spring-boot:run'], {
        cwd: backendDir,
        stdio: ['ignore', 'pipe', 'pipe'],
        env: { ...process.env, ...oauthEnv, SERVER_PORT: String(BACKEND_PORT) },
      });
    }

    backendProcess.stdout?.on('data', (data: Buffer) => {
      console.log(`[Backend] ${data.toString().trim()}`);
    });

    backendProcess.stderr?.on('data', (data: Buffer) => {
      console.error(`[Backend ERR] ${data.toString().trim()}`);
    });

    backendProcess.on('error', (err) => {
      console.error('[Electron] Error al iniciar backend:', err);
      const enoent = (err as NodeJS.ErrnoException).code === 'ENOENT';
      reject(enoent
        ? new Error(`No se encontró "${JAVA_BIN}". Instala Java 21 o empaqueta el JRE (build-jre.sh).`)
        : err);
    });

    backendProcess.on('exit', (code) => {
      console.log(`[Electron] Backend terminado con código ${code}`);
      backendProcess = null;
    });

    // Health check polling
    pollHealth(resolve, reject, 0);
  });
}

function pollHealth(resolve: () => void, reject: (err: Error) => void, attempt: number) {
  let done = false;

  const retry = () => {
    if (done) return;
    if (attempt >= 30) {
      done = true;
      reject(new Error('Timeout esperando al backend'));
      return;
    }
    setTimeout(() => pollHealth(resolve, reject, attempt + 1), 1000);
  };

  const req = http.get(HEALTH_URL, (res) => {
    if (done) return;
    if (res.statusCode === 200) {
      done = true;
      console.log('[Electron] Backend listo');
      resolve();
    } else {
      retry();
    }
    res.resume();
  });

  req.on('error', retry);
  req.setTimeout(3000, () => {
    req.destroy();
    retry();
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
  splash.loadURL('file://' + path.resolve(__dirname, '..', 'splash.html'));

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

app.whenReady().then(async () => {
  try {
    // Construir frontend si no existe (lo sirve el backend desde frontend/dist/)
    matarProcesosAnteriores();
    await ensureFrontendBuilt();
    await startBackend();
    // Detectar si Vite dev server esta corriendo (desarrollo)
    APP_URL = await detectFrontendUrl();
    console.log(`[Electron] Abriendo ${APP_URL}`);
    createWindow();
  } catch (err) {
    console.error('[Electron] Error al iniciar:', err);
    dialog.showErrorBox('Error', `No se pudo iniciar el backend: ${err}`);
    app.quit();
  }
});

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
