# Linux — instalación desde código fuente (sin sudo)

Instala la app en `~/.eMailAI` compilando el proyecto: wrapper Electron +
backend jar con frontend embebido + JRE propio. Nada fuera de:

- `~/.eMailAI/` — programa y datos
- `~/.local/bin/emailai` — lanzador (start/stop/restart/status/log)
- `~/.local/share/applications/` + iconos — entrada de menú KDE

## Uso

```bash
# 1. Compilar todo (jar + frontend + electron unpacked)
bash lanzadores/linux/sourcecode/build-package.sh            # con tests
bash lanzadores/linux/sourcecode/build-package.sh --skip-tests

# 2. Empaquetar el wrapper (genera linux-unpacked con JRE jlink)
cd electron && npm run dist:dir

# 3. Instalar
cd .. && bash lanzadores/linux/sourcecode/install.sh

# Desinstalar (pide confirmación, permite conservar datos)
bash lanzadores/linux/sourcecode/uninstall.sh
```

Requisitos: Java 21, Maven, Node 22, pnpm, `electron-builder` (viene con
`npm install` en electron/).

## Lanzador `emailai`

| Comando | Acción |
|---|---|
| `emailai start` | Arranca la app (ventana + backend en puerto efímero) |
| `emailai stop` | Detiene procesos |
| `emailai restart` | Stop + start |
| `emailai status` | ¿Corriendo? |
| `emailai log [N]` | Últimas N líneas del log |

El backend NO usa puerto fijo: puerto efímero + ready file + protocolo
interno `app://` de Electron. La configuración OAuth vive en
`~/.eMailAI/oauth-config.json` (chmod 600).
