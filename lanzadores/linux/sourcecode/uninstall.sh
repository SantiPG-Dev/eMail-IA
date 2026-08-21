#!/usr/bin/env bash
# ============================================================
# eMail-IA — Desinstalador de la instalación local (~/.eMailAI)
# ============================================================
# Elimina el programa, el lanzador, el icono y la entrada de
# menú. PIDE CONFIRMACIÓN y ofrece conservar tus datos (BD de
# correos, credenciales, modelos IA) por si reinstalas.
set -euo pipefail

APP_DIR="$HOME/.eMailAI"
BIN="$HOME/.local/bin/emailai"
DESKTOP="$HOME/.local/share/applications/emailai.desktop"
ICON="$HOME/.local/share/icons/hicolor/256x256/apps/emailai.png"

ok()   { printf '  \033[1;32m✔\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m✘ ERROR: %s\033[0m\n' "$*" >&2; exit 1; }

printf '\n\033[1;36m eMail-IA — Desinstalador\033[0m\n\n'
[[ -d "$APP_DIR" ]] || fail "No hay instalación en $APP_DIR"

printf 'Esto elimina:\n'
du -sh "$APP_DIR" 2>/dev/null | sed 's/^/  • /'
[[ -f "$BIN" ]]    && printf '  • %s\n' "$BIN"
[[ -f "$DESKTOP" ]] && printf '  • %s\n' "$DESKTOP"
[[ -f "$ICON" ]]   && printf '  • %s\n' "$ICON"
printf '\n  Tus DATOS (correos, cuentas, modelos IA) están en %s/DB\n' "$APP_DIR"
printf '  y oauth-config.json en %s\n\n' "$APP_DIR"

read -rp '¿Eliminar también tus datos? [s/N] ' borrar_datos
read -rp '¿Desinstalar eMail-IA? [s/N] ' confirmar
[[ "$confirmar" == "s" || "$confirmar" == "S" ]] || fail "Cancelado por el usuario"

"$BIN" stop >/dev/null 2>&1 || true
pkill -f -- "emailai[-]electron" 2>/dev/null || true
pkill -f -- "java .*emailai[-]backend\.jar" 2>/dev/null || true

if [[ "$borrar_datos" == "s" || "$borrar_datos" == "S" ]]; then
    rm -rf "$APP_DIR"
    ok "Programa y datos eliminados ($APP_DIR)"
else
    rm -rf "$APP_DIR/app" "$APP_DIR"/emailai-backend.jar \
           "$APP_DIR"/emailai.pid "$APP_DIR"/emailai.log 2>/dev/null || true
    ok "Programa eliminado; datos conservados en $APP_DIR (DB, oauth-config.json)"
fi
rm -f "$BIN" "$DESKTOP" "$ICON"
command -v update-desktop-database >/dev/null && update-desktop-database "$(dirname "$DESKTOP")" 2>/dev/null || true
ok "Lanzador, icono y entrada de menú eliminados"
printf '\n\033[1;32m✔ eMail-IA desinstalada\033[0m\n\n'
