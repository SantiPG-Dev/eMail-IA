#!/usr/bin/env bash
# Build the eMail-IA RPM via a hand-written spec.
# Bypasses electron-builder's bundled fpm 1.9.3 (broken on modern rpmbuild).
# Requires: electron/release/linux-unpacked/ (produced by `electron-builder --dir`
# or `npm run dist:linux` in electron/).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"   # raíz del repo
ELECTRON_DIR="$ROOT_DIR/electron"

if [ ! -d "$ELECTRON_DIR/release/linux-unpacked" ]; then
  echo ">> electron/release/linux-unpacked missing, running electron-builder --dir…"
  (cd "$ELECTRON_DIR" && npx tsc && npx electron-builder --dir)
fi

# Directorio de build del RPM dentro de electron/release (junto al resto de artefactos)
RPM_TOP="$ELECTRON_DIR/release/rpm"
mkdir -p "$RPM_TOP"/{BUILD,RPMS,SOURCES,SPECS,SRPMS}

rpmbuild -bb \
  --define "_topdir $RPM_TOP" \
  --define "emailai_src $ELECTRON_DIR/release/linux-unpacked" \
  --define "emailai_icon $ELECTRON_DIR/assets/icon-256.png" \
  "$SCRIPT_DIR/emailai-electron.spec"

echo "✓ RPM → $RPM_TOP/RPMS/x86_64/"
ls -lh "$RPM_TOP"/RPMS/x86_64/*.rpm
