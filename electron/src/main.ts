import {
  app,
  BrowserWindow,
  Tray,
  Menu,
  nativeImage,
  session,
  protocol,
  dialog,
  shell,
} from "electron";
import * as path from "path";
import * as fs from "fs";
import * as http from "http";
import {
  startBackend,
  stopBackend,
  matarProcesosAnteriores,
  ensureFrontendBuilt,
  puertoBackend,
} from "./backend";
import { crearProxyBackend, CSP, esUrlNavegable, esOrigenLocalPermitido } from "./proxy";
import { registrarIpc } from "./ipc";

// Proceso main: config de arranque, ventana, splash, tray y lifecycle.
// El backend Java vive en backend.ts, el proxy app:// y la CSP en proxy.ts
// y los handlers del preload en ipc.ts.

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
// Idioma del renderer: sin esto los <input type="date"> y demás controles
// nativos heredan en-US y pintan las fechas MM/DD/YYYY. Debe ir antes de ready.
app.commandLine.appendSwitch("lang", "es-ES");

const DEV_FRONTEND = "http://localhost:5173";
const APP_ORIGIN = "app://local";
let APP_URL = `${APP_ORIGIN}/`;

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

let mainWindow: BrowserWindow | null = null;
let tray: Tray | null = null;

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
    icon: path.join(__dirname, "..", "assets", "icon-512.png"),
    webPreferences: { nodeIntegration: false, contextIsolation: true },
  });

  // Cargar splash HTML desde archivo
  splash.loadURL(
    "file://" +
      path.resolve(__dirname, "..", "splash.html") +
      "?v=" +
      app.getVersion(),
  );

  return splash;
}

// ── Ventana principal ────────────────────────────────────────────
async function createWindow() {
  await session.defaultSession.clearCache().catch(() => {});
  await session.defaultSession
    .clearStorageData({
      storages: ["localstorage", "serviceworkers", "cachestorage"],
    })
    .catch(() => {});

  // Mostrar splash mientras carga
  const splash = showSplash();

  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    title: "eMail-IA",
    icon: path.join(__dirname, "..", "assets", "icon-512.png"),
    show: false,
    backgroundColor: "#0F172A",
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      nodeIntegration: false,
      contextIsolation: true,
    },
  });

  // Deshabilitar caché HTTP
  mainWindow.webContents.session.webRequest.onBeforeSendHeaders(
    { urls: ["*://*/*"] },
    (details: any, callback: any) => {
      callback({
        requestHeaders: {
          ...details.requestHeaders,
          "Cache-Control": "no-cache, no-store, must-revalidate",
          Pragma: "no-cache",
        },
      });
    },
  );

  mainWindow.loadURL(APP_URL);

  // Visibilidad de errores del renderer en el log del main (diagnóstico E2E)
  mainWindow.webContents.on("did-fail-load", (_e, code, desc, url) =>
    console.error(`[Electron] did-fail-load ${code} ${desc} ${url}`),
  );
  mainWindow.webContents.on("console-message", (_e, level, message) => {
    if (level >= 2) console.warn(`[Renderer] ${message}`);
  });

  // Esperar a que React termine y luego hacer transición
  mainWindow.webContents.on("did-finish-load", () => {
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
      return { action: "allow" };
    }
    if (esUrlNavegable(url)) {
      shell.openExternal(url);
    } else {
      console.warn(
        `[Electron] Ventana/openExternal bloqueado (URL no permitida): ${url}`,
      );
    }
    return { action: "deny" };
  });

  // La ventana principal solo navega dentro de la propia app (SPA); cualquier
  // navegación top-level a un origen ajeno se bloquea.
  mainWindow.webContents.on("will-navigate", (event, url) => {
    if (url !== APP_URL && !esOrigenLocalPermitido(url)) {
      console.warn(`[Electron] Navegación top-level bloqueada: ${url}`);
      event.preventDefault();
    }
  });

  mainWindow.on("closed", () => {
    mainWindow = null;
  });

  // Tray icon (64x64 para mejor visibilidad en HiDPI)
  try {
    let trayIconPath = path.join(__dirname, "..", "assets", "icon-128.png");
    if (!fs.existsSync(trayIconPath)) {
      trayIconPath = path.join(__dirname, "..", "assets", "icon-512.png");
    }
    if (fs.existsSync(trayIconPath)) {
      const trayIcon = nativeImage.createFromPath(trayIconPath);
      tray = new Tray(trayIcon);
      tray.setToolTip("eMail-IA");
      const contextMenu = Menu.buildFromTemplate([
        { label: "Abrir eMail-IA", click: () => mainWindow?.show() },
        { type: "separator" },
        { label: "Salir", click: () => app.quit() },
      ]);
      tray.setContextMenu(contextMenu);
      tray.on("click", () => mainWindow?.show());
    }
  } catch (e) {
    console.log("[Electron] Tray no disponible:", e);
  }
}

// ── App lifecycle ────────────────────────────────────────────────
registrarIpc();

// ── Instancia única ──────────────────────────────────────────────
// Con puertos efímeros dos instancias NO chocan por red, pero sí por la BD H2
// (file lock). El segundo lanzamiento activa la ventana de la primera y muere.
const gotTheLock = app.requestSingleInstanceLock();
if (gotTheLock) {
  app.on("second-instance", () => {
    if (mainWindow) {
      mainWindow.show();
      mainWindow.focus();
    }
  });

  app.whenReady().then(async () => {
    try {
      // El renderer vive en app://local/; el main lo proxya al backend
      protocol.handle("app", crearProxyBackend(puertoBackend));

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
                "Content-Security-Policy": [CSP.dev],
              },
            });
          },
        );
        console.log(
          `[Electron] Dev: UI en ${viteUrl} (backend externo en 8080 vía proxy Vite)`,
        );
      } else {
        matarProcesosAnteriores();
        await ensureFrontendBuilt();
        await startBackend();
      }
      console.log(`[Electron] Abriendo ${APP_URL}`);
      createWindow();
    } catch (err) {
      console.error("[Electron] Error al iniciar:", err);
      dialog.showErrorBox("Error", `No se pudo iniciar el backend: ${err}`);
      app.quit();
    }
  });
} else {
  app.quit();
}

app.on("window-all-closed", () => {
  stopBackend();
  if (process.platform !== "darwin") {
    app.quit();
  }
});

app.on("before-quit", () => {
  stopBackend();
});

app.on("activate", () => {
  if (mainWindow === null) {
    createWindow();
  }
});
