# Electron — eMail-IA (shell de escritorio)

Wrapper **Electron** que envuelve la app de escritorio.

## Arquitectura

```
Electron (main process)
├─ spawn: java -jar backend.jar --server.port=0 --emailai.ready-file=...
│    (puerto efímero asignado por el SO en 127.0.0.1 — sin puertos fijos)
├─ espera el ready file {"port":N,"pid":M} que escribe el backend al estar listo
├─ protocolo app:// (protocol.handle): el renderer carga app://local/ y el
│  main hace de proxy hacia 127.0.0.1:<efímero> (Authorization, adjuntos...)
├─ BrowserWindow → app://local/ (frontend React servido por el backend vía proxy)
├─ instancia única (requestSingleInstanceLock): evita dos JVM sobre la misma BD H2
├─ tray icon + notificaciones
└─ on-quit → kill proceso backend (el backend borra su ready file al salir)
```

Puerto fijo restante: **9876** (callback OAuth loopback, solo durante el flujo
de alta de cuentas Google/Microsoft). Si los clientes OAuth están registrados
como "Desktop/App instalada", se puede cambiar `OAuthService.CALLBACK_PORT`
a 0 y construir el `redirect_uri` con el puerto real.

## Requisitos

- Java 21+ (para el backend JAR; el AppImage/deb/rpm empaqueta su propio JRE)
- Node.js 22+

## Desarrollo

Flujo completo (Electron + Vite + backend):

```bash
# 1. Backend en 8080 (terminal 1)
cd backend && mvn spring-boot:run

# 2. Vite en 5173 (terminal 2) — su proxy /api → 8080
cd frontend && pnpm dev

# 3. Electron (terminal 3) — detecta Vite y no lanza backend propio
cd electron && npm run dev
```

Electron solo (UI empaquetada en el jar, sin Vite):

```bash
cd backend && mvn clean package -DskipTests
cd ../electron && npm run dev    # lanza el jar con puerto efímero + app://
```

## Estructura

```
electron/
├── src/
│   ├── main.ts        # Proceso principal: spawn backend, ventana, lifecycle
│   └── preload.ts     # IPC seguro (contextBridge)
├── assets/
│   ├── icon-32.png
│   └── icon-256.png
├── dist/              # Compilado TypeScript (main.js, preload.js)
├── package.json
└── tsconfig.json
```
