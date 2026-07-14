# Electron — eMail-IA (shell de escritorio)

Wrapper **Electron** que envuelve la app para que siga siendo una **app de escritorio independiente** (no web).

## Arquitectura (Fase 9+)

```
Electron (main process)
├─ spawn: java -jar backend.jar --port=<puerto>
├─ health-check: poll http://localhost:<puerto>/health
├─ BrowserWindow → http://localhost:<puerto>
├─ tray, notificaciones, diálogos nativos
├─ shell.openExternal() para OAuth (abre navegador del SO)
└─ on-quit → kill proceso backend
```

## Stack previsto

- **Electron** + electron-builder
- **TypeScript** (main + preload)
- IPC seguro via preload
- Packaging: `.deb` / `.AppImage` / `.dmg` / `.exe`
- Backend JAR + JRE (jpackage) bundlados como recursos

## Estructura prevista

```
electron/
├── src/
│   ├── main.ts        # spawn backend, BrowserWindow, lifecycle
│   ├── preload.ts     # IPC seguro
│   └── tray.ts        # tray icon, notificaciones
├── assets/            # iconos (icon-256.png)
├── electron-builder.yml
└── package.json
```

> Fase 0 completada — scaffolding. La implementación empieza en Fase 9.
