# Linux — Flatpak (APLAZADO)

Decisión (ago 2026): aplazado. El AppImage + deb/rpm + fuente cubren el
nicho de Linux y el Flatpak añade coste de mantenimiento y fricción real
con el sandbox.

## Por qué está aplazado

- **Datos**: la BD H2 cifrada y `oauth-config.json` vivirían en
  `~/.var/app/com.santipg.emailai/` — distinto del resto de instaladores
  (migración necesaria si el usuario ya usó AppImage/deb).
- **OAuth loopback (9876)**: `--socket=network` + permiso de host
  `localhost` funciona, pero abrir el navegador externo pasa por el portal
  xdg-desktop-portal → fricción y casos borde.
- **JRE jlink**: funciona dentro del sandbox (no necesita java del runtime),
  pero el tamaño (~69 MB) hace que el repo de flathub penalice.

## Diseño cuando se retome

- Manifest `com.santipg.emailai.yml`, runtime `org.freedesktop.Platform 24.08`
  + `org.electronjs.Electron2.BaseApp` (comparte libs de Electron con el SDK).
- Empaquetar `electron/release/linux-unpacked` + `jre/` como fuentes locales
  (build en CI, no flathub: el jar lleva secretos de build).
- `finish-args`: `--socket=wayland`, `--socket=fallback-x11`, `--device=dri`,
  `--socket=network` (backend loopback + IMAP/SMTP), `--talk-name=org.freedesktop.Notifications`.
- Icono y appdata de `electron/assets/`.
