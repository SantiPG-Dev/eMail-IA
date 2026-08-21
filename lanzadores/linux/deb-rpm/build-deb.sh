#!/usr/bin/env bash
# Build the eMail-IA .deb via electron-builder (usa el JRE jlink de electron/jre).
# Requiere: node_modules de electron/ (npm install) y el jar en backend/target/.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"   # raíz del repo
ELECTRON_DIR="$ROOT_DIR/electron"

cd "$ELECTRON_DIR"
bash build-jre.sh
npx tsc
npx electron-builder --linux deb

echo "✓ DEB →"
ls -lh release/*.deb
