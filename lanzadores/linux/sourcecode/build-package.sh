#!/usr/bin/env bash
# Build & Package script para eMail-IA
# Uso: bash lanzadores/linux/sourcecode/build-package.sh [--skip-tests]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)" # raíz del repo
SKIP_TESTS=false
[[ "${1:-}" == "--skip-tests" ]] && SKIP_TESTS=true

echo "=========================================="
echo " eMail-IA — Build & Package"
echo "=========================================="

echo "[1/4] Backend..."
cd "$ROOT_DIR/backend"
$SKIP_TESTS && mvn clean package -DskipTests -q || mvn clean package -q
echo "       JAR: backend/target/emailai-backend-1.3.0.jar"

echo "[2/4] Frontend..."
cd "$ROOT_DIR/frontend"
pnpm install --ignore-scripts --frozen-lockfile -q 2>/dev/null
# tsc y vite a pelo: `pnpm build` purga node_modules cuando no hay TTY
./node_modules/.bin/tsc -b && ./node_modules/.bin/vite build
echo "       Build: frontend/dist/"

echo "[3/4] Electron..."
cd "$ROOT_DIR/electron"
npm install --silent
npm run build
echo "       TS compilado: electron/dist/"

if ! $SKIP_TESTS; then
  echo "[4/4] Tests..."
  cd "$ROOT_DIR/backend" && mvn test -q
  cd "$ROOT_DIR/frontend" && pnpm test
fi

echo ""
echo "Build OK. Para empaquetar: cd electron && npm run dist:linux (JRE jlink + AppImage + deb)"
