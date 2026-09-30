import { app } from "electron";
import * as path from "path";
import * as fs from "fs";
import * as os from "os";
import { spawn, execSync, type ChildProcess } from "child_process";

// Ciclo de vida del backend Java: localizar java/jar, limpiar huérfanos,
// arrancar (jar o mvn), esperar el ready file y parar. El puerto efímero
// que publica el backend se consulta con puertoBackend() (lo consume el proxy).

const BACKEND_JAR = findJar();
const JAVA_BIN = findJava();
const READY_FILE = resolveReadyFile();

let backendProcess: ChildProcess | null = null;
let backendPort: number | null = null;

export function puertoBackend(): number | null {
  return backendPort;
}

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
export function matarProcesosAnteriores() {
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
  //    wrapper sh -c que ejecuta este pkill (gotcha que ya mordió una vez):
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

// ── Spawn backend ────────────────────────────────────────────────
function ensureFrontendBuilt(): Promise<void> {
  return new Promise((resolve) => {
    // En instalación empaquetada (~/.eMailAI, --jar= o app empaquetada) el
    // frontend está EMBEBIDO en el jar (BOOT-INF/classes/static): no hay nada
    // que compilar. Solo tiene sentido construir en el checkout de desarrollo.
    const jarArg = process.argv.find((a) => a.startsWith("--jar="));
    const frontendDir = path.resolve(__dirname, "..", "..", "frontend");
    if (
      jarArg ||
      app.isPackaged ||
      !fs.existsSync(path.join(frontendDir, "package.json"))
    ) {
      console.log("[Electron] Frontend embebido en el jar — no se compila");
      resolve();
      return;
    }

    const indexPath = path.join(frontendDir, "dist", "index.html");
    if (fs.existsSync(indexPath)) {
      resolve();
      return;
    }

    console.log("[Electron] Construyendo frontend React...");
    const pnpm = spawn("pnpm", ["build"], { cwd: frontendDir, stdio: "pipe" });
    // Sin este handler, "pnpm" ausente lanza ENOENT no capturado y tumba la app
    pnpm.on("error", (err) => {
      console.warn(
        `[Electron] No se pudo lanzar pnpm (${err.message}); el backend usa su fallback`,
      );
      resolve();
    });
    pnpm.on("close", (code) => {
      if (code === 0) console.log("[Electron] Frontend construido");
      else
        console.warn(
          `[Electron] Frontend build fallo (codigo ${code}), usando fallback`,
        );
      resolve(); // Seguir aunque falle
    });
  });
}

export function startBackend(): Promise<void> {
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
      // Heap capado a pelo: sin techo el JVM se come el 25% de la RAM y arranca
      // con ~1,5% de heap inicial. Si Weka/H2 se quedan cortos, EMAILAI_XMX sube el techo.
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

export function stopBackend() {
  if (backendProcess) {
    console.log("[Electron] Deteniendo backend...");
    backendProcess.kill("SIGTERM");
    setTimeout(() => {
      if (backendProcess) {
        backendProcess.kill("SIGKILL");
        backendProcess = null;
      }
    }, 5000);
  }
}

export { ensureFrontendBuilt };
