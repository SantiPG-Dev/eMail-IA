import { app, BrowserWindow, shell, dialog, Tray, Menu, Notification, nativeImage } from 'electron';
import * as path from 'path';
import * as fs from 'fs';
import { spawn, ChildProcess } from 'child_process';
import * as http from 'http';

// ── Config ──────────────────────────────────────────────────────
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
function findJar(): string {
  // Prioridad: 1) argumento --jar, 2) desarrollo en ../backend/target, 3) junto al ejecutable
  const jarArg = process.argv.find(a => a.startsWith('--jar='));
  if (jarArg) return jarArg.slice('--jar='.length);

  const devJar = path.resolve(__dirname, '..', '..', 'backend', 'target', 'emailai-backend-1.0-SNAPSHOT.jar');
  if (fs.existsSync(devJar)) return devJar;

  // En producción, el JAR está en resources/
  const prodJar = path.join(process.resourcesPath || '', 'backend.jar');
  if (fs.existsSync(prodJar)) return prodJar;

  throw new Error(`No se encontró el JAR del backend. Buscado en: ${devJar}, ${prodJar}`);
}

// ── Spawn backend ────────────────────────────────────────────────
function startBackend(): Promise<void> {
  return new Promise((resolve, reject) => {
    const jar = BACKEND_JAR;
    console.log(`[Electron] Iniciando backend: java -jar ${jar} --server.port=${BACKEND_PORT}`);

    backendProcess = spawn('java', [
      '-jar', jar,
      `--server.port=${BACKEND_PORT}`,
      '--emailai.data-dir=DB',
    ], {
      stdio: ['ignore', 'pipe', 'pipe'],
    });

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
  if (attempt > 30) { // ~30 segundos máximo
    reject(new Error('Timeout esperando al backend'));
    return;
  }

  const req = http.get(HEALTH_URL, (res) => {
    if (res.statusCode === 200) {
      console.log('[Electron] Backend listo');
      resolve();
    } else {
      setTimeout(() => pollHealth(resolve, reject, attempt + 1), 1000);
    }
  });

  req.on('error', () => {
    setTimeout(() => pollHealth(resolve, reject, attempt + 1), 1000);
  });

  req.setTimeout(3000, () => {
    req.destroy();
    setTimeout(() => pollHealth(resolve, reject, attempt + 1), 1000);
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

// ── Ventana principal ────────────────────────────────────────────
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    title: 'eMail-IA',
    icon: path.join(__dirname, '..', 'assets', 'icon-256.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
    },
  });

  mainWindow.loadURL(APP_URL);

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

  // Tray icon
  try {
    const iconPath = path.join(__dirname, '..', 'assets', 'icon-32.png');
    if (fs.existsSync(iconPath)) {
      tray = new Tray(nativeImage.createFromPath(iconPath));
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
