import { app, BrowserWindow, shell, dialog, Tray, Menu, Notification, nativeImage, session } from 'electron';
import * as path from 'path';
import * as fs from 'fs';
import { spawn, execSync, ChildProcess } from 'child_process';
import * as http from 'http';

// ── Config ──────────────────────────────────────────────────────
// El backend Spring Boot se ejecuta como proceso hijo en el puerto 8420.
// Electron detecta si Vite dev está corriendo (puerto 5173) y usa esa URL,
// o si no, sirve el build estático desde el backend embeebido.

process.env.ELECTRON_ENABLE_STACK_DUMPING = 'false';

const BACKEND_PORT = 8420;
const HEALTH_URL = `http://localhost:${BACKEND_PORT}/health`;
const DEV_FRONTEND = 'http://localhost:5173';
let APP_URL = `http://localhost:${BACKEND_PORT}`;
const BACKEND_JAR = findJar();

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

let mainWindow: BrowserWindow | null = null;
let backendProcess: ChildProcess | null = null;
let tray: Tray | null = null;

// ── Buscar el JAR del backend ────────────────────────────────────
function findJar(): string | null {
  // 1) Argumento --jar
  const jarArg = process.argv.find(a => a.startsWith('--jar='));
  if (jarArg) return jarArg.slice('--jar='.length);

  // 2) Desarrollo: JAR compilado en backend/target/
  const devJar = path.resolve(__dirname, '..', '..', 'backend', 'target', 'emailai-backend-1.0-SNAPSHOT.jar');
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
    const frontendDir = path.resolve(__dirname, '..', '..', 'frontend');
    const indexPath = path.join(frontendDir, 'dist', 'index.html');
    if (fs.existsSync(indexPath)) { resolve(); return; }

    console.log('[Electron] Construyendo frontend React...');
    const pnpm = spawn('pnpm', ['build'], { cwd: frontendDir, stdio: 'pipe' });
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
      console.log(`[Electron] Iniciando backend: java -jar ${BACKEND_JAR}`);
      backendProcess = spawn('java', [
        '-jar', BACKEND_JAR,
        `--server.port=${BACKEND_PORT}`,
        '--emailai.data-dir=DB',
      ], { stdio: ['ignore', 'pipe', 'pipe'] });
    } else {
      const backendDir = path.resolve(__dirname, '..', '..', 'backend');
      console.log(`[Electron] Iniciando backend: mvn spring-boot:run en ${backendDir}`);
      backendProcess = spawn('mvn', ['spring-boot:run'], {
        cwd: backendDir,
        stdio: ['ignore', 'pipe', 'pipe'],
        env: { ...process.env, SERVER_PORT: String(BACKEND_PORT) },
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
      reject(err);
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
  // Limpiar TODO: caché, storage, cookies, service workers
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

  // Abrir enlaces externos en el navegador del sistema (OAuth)
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('http://localhost')) {
      return { action: 'allow' };
    }
    shell.openExternal(url);
    return { action: 'deny' };
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
