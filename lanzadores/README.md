# Instaladores y lanzadores de eMail-IA

Un directorio por plataforma. Todos los artefactos son **autosuficientes**:
llevan Electron + backend.jar + JRE 21 (jlink) — no requieren Java instalado.

| Plataforma | Carpeta | Artefacto | Estado |
|---|---|---|---|
| Linux (cualquier distro) | — | `*.AppImage` | ✅ |
| Linux Debian/Ubuntu | `linux/deb-rpm/` | `*.deb` | ✅ |
| Linux Fedora/RHEL | `linux/deb-rpm/` | `*.rpm` | ✅ |
| Linux desde fuente | `linux/sourcecode/` | `~/.eMailAI` (sin sudo) | ✅ |
| Linux Flatpak | `linux/flatpak/` | — | ⏸️ aplazado |
| Windows 10/11 x64 | `windows/` | `*-setup.exe` (NSIS) | 🔧 via CI |
| macOS Apple Silicon | `macos/` | `*.dmg` | 🔧 via CI |

## Cómo obtener los instaladores

### Release de GitHub (recomendado)

Los builds los genera [`.github/workflows/release.yml`](../.github/workflows/release.yml):

- **Manual**: pestaña *Actions* → *Release — instaladores multiplataforma* → *Run workflow*
- **Por tag**: al pushar `git tag v1.0.0 && git push --tags` se crea un
  **release draft** con AppImage, deb, rpm, exe, dmg y sus SHA256

### Construcción local (Linux)

```bash
# Prerrequisitos: Java 21 (jlink), Node 22, pnpm, Maven
bash lanzadores/linux/sourcecode/build-package.sh --skip-tests   # jar + frontend + electron TS

cd electron && npm run dist:linux          # AppImage + deb (genera el JRE jlink)
bash ../lanzadores/linux/deb-rpm/build-rpm.sh   # rpm
```

Windows y macOS **no se pueden construir desde Linux** (el JRE jlink no
cross-compila SO): usar el workflow de GitHub Actions o una máquina del SO.

## ¿Qué instalar en cada caso?

- **Usuario final, cualquier Linux** → AppImage (doble clic, sin instalación)
- **Debian/Ubuntu con menú integrado** → deb (`sudo apt install ./emailai-electron-*.deb`)
- **Fedora/RHEL** → rpm (`sudo dnf install ./emailai-electron-*.rpm`)
- **Sin permisos root / desde código** → `linux/sourcecode/install.sh`
- **Windows** → `-setup.exe` (NSIS)
- **macOS** → `.dmg` (arrastrar a Aplicaciones; sin Developer ID, Gatekeeper
  avisará: clic derecho → Abrir, la primera vez)

## Datos de la aplicación

Independiente del instalador, todo va a `~/.config/emailai-electron/`
(BD H2 cifrada, config, `oauth-config.json`, `backend.ready`). Desinstalar
el paquete **no** borra los datos del usuario.

## Detalles técnicos

- **Sin puertos fijos**: el backend escucha en un puerto efímero (127.0.0.1)
  elegido por el SO; Electron lo descubre por el ready file y expone la app
  al renderer vía protocolo interno `app://local/`.
- **JRE jlink** (`electron/build-jre.sh`): java.se + crypto.ec + charsets +
  localedata + zipfs + unsupported (~69 MB). Se regenera en cada build de
  distinto SO/arquitectura — por eso cada plataforma se construye en su runner.
- **RPM**: spec propio (`linux/deb-rpm/emailai-electron.spec`) porque el fpm
  1.9.3 que trae electron-builder se rompe con rpmbuild moderno.
