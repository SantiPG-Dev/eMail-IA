# Linux — deb y rpm

Paquetes de sistema con integración de menú (`.desktop` + icono). Ambos
instalan en `/opt/eMail-IA` y llevan el JRE jlink embebido (sin Java del sistema).

## Construir

```bash
# Desde la raíz del repo (necesita electron/ compilado: npm install en electron/)
bash lanzadores/linux/deb-rpm/build-deb.sh   # → electron/release/*.deb
bash lanzadores/linux/deb-rpm/build-rpm.sh   # → electron/release/rpm/RPMS/x86_64/*.rpm
```

Requisitos: Java 21 (jlink), Node 22, Maven, pnpm, `rpmbuild` (para rpm).

## Instalar

```bash
# Debian / Ubuntu / Mint
sudo apt install ./emailai-electron-1.0.0-amd64.deb

# Fedora / RHEL
sudo dnf install ./emailai-electron-1.0.0-1.x86_64.rpm
```

Desinstalar: `sudo apt remove emailai-electron` / `sudo dnf remove emailai-electron`.
Los datos del usuario (`~/.config/emailai-electron/`) no se tocan.

## Dependencias declaradas (Electron 33)

gtk3, libnotify, nss, libXScrnSaver, libXtst, alsa-lib, at-spi2-core, xdg-utils.

AppImage además requiere FUSE (libfuse2) en el sistema anfitrión.
