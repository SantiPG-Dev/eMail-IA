#!/usr/bin/env bash
# ============================================================
# eMail-IA — Instalador local sin sudo en ~/.eMailAI
# ============================================================
# Instala la aplicación de escritorio (wrapper Electron + backend
# Java con frontend embebido) y mantiene TODO el flujo de la app
# dentro de ~/.eMailAI: BD H2, modelos Weka, jwt.key, logs y
# configuración OAuth. Nada se escribe fuera de:
#
#   ~/.eMailAI/                          programa + datos
#   ~/.local/bin/emailai                 ejecutable (PATH)
#   ~/.local/share/applications/         entrada de menú KDE
#   ~/.local/share/icons/hicolor/        icono
#
# Uso:          bash lanzadores/linux/sourcecode/install.sh
# Desinstalar:  bash lanzadores/linux/sourcecode/uninstall.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"   # raíz del repo
APP_DIR="$HOME/.eMailAI"
BIN_DIR="$HOME/.local/bin"
JAR_SRC="$ROOT/backend/target/emailai-backend-1.0.0.jar"
JAR_DST="$APP_DIR/emailai-backend.jar"
ELECTRON_SRC="$ROOT/electron/release/linux-unpacked"
ELECTRON_DST="$APP_DIR/app"

ok()   { printf '  \033[1;32m✔\033[0m %s\n' "$*"; }
pass() { printf '\033[1;32m✔ %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31m✘ ERROR: %s\033[0m\n' "$*" >&2; exit 1; }
size() { du -sh "$1" 2>/dev/null | cut -f1; }

banner() {
    printf '\n\033[1;36m══════════════════════════════════════════════════════════════\033[0m\n'
    printf '\033[1;36m %s\033[0m\n' "$*"
    printf '\033[1;36m══════════════════════════════════════════════════════════════\033[0m\n'
}

banner "eMail-IA — Instalador local (sin sudo)"
printf '  Destino de la instalación: \033[1m%s\033[0m\n' "$APP_DIR"
printf '  Este script instala: wrapper Electron + backend Java + menú KDE\n\n'

# ── Paso 1: comprobar que está todo compilado ─────────────────────────
printf '\033[1m[1/6] Comprobando artefactos compilados\033[0m\n'
[[ -f "$JAR_SRC" ]] || fail "No existe $JAR_SRC
       Compila primero:  cd frontend && pnpm build && cd ../backend && mvn package -DskipTests"
ok "Backend:  $JAR_SRC ($(size "$JAR_SRC"))"

[[ -d "$ELECTRON_SRC" ]] || fail "No existe $ELECTRON_SRC
       Compila primero:  cd electron && npm run dist:dir"
ok "Electron: $ELECTRON_SRC ($(size "$ELECTRON_SRC"))"

# ── Paso 2: copiar el programa a ~/.eMailAI ───────────────────────────
printf '\033[1m[2/6] Copiando programa a %s\033[0m\n' "$APP_DIR"
if [[ -d "$ELECTRON_DST" || -f "$JAR_DST" ]]; then
    printf '  Ya había una instalación: se actualiza (tus datos en DB/ se conservan)\n'
    rm -rf "$ELECTRON_DST"
fi
mkdir -p "$APP_DIR" "$BIN_DIR"
printf '  Copiando wrapper Electron (%s)…\n' "$(size "$ELECTRON_SRC")"
cp -a "$ELECTRON_SRC" "$ELECTRON_DST"
ok "Wrapper instalado en $ELECTRON_DST"
printf '  Copiando backend Java (%s)…\n' "$(size "$JAR_SRC")"
cp -f "$JAR_SRC" "$JAR_DST"
ok "Backend instalado en $JAR_DST"

# ── Paso 3: configuración OAuth ───────────────────────────────────────
printf '\033[1m[3/6] Configuración OAuth\033[0m\n'
OAUTH_DST="$APP_DIR/oauth-config.json"
chmod 600 "$OAUTH_DST" 2>/dev/null || true  # contiene clientSecrets: owner-only
if [[ ! -f "$OAUTH_DST" ]]; then
    if [[ -f "$ROOT/electron/oauth-config.json" ]]; then
        cp "$ROOT/electron/oauth-config.json" "$OAUTH_DST"
        chmod 600 "$OAUTH_DST"
        ok "Copiado desde electron/oauth-config.json"
    else
        cat > "$OAUTH_DST" <<'EOF'
{
  "google": { "clientId": "", "clientSecret": "" },
  "microsoft": { "clientId": "", "clientSecret": "" }
}
EOF
        ok "Creada plantilla $OAUTH_DST (rellénala para usar OAuth)"
    fi
else
    ok "Existente conservado: $OAUTH_DST"
fi

# ── Paso 4: lanzador ~/.local/bin/emailai ─────────────────────────────
printf '\033[1m[4/6] Instalando lanzador en %s/emailai\033[0m\n' "$BIN_DIR"
cat > "$BIN_DIR/emailai" <<EOF
#!/usr/bin/env bash
# eMail-IA — lanzador de la instalación local (~/.eMailAI)
# Sin puertos: el backend arranca en un puerto efímero elegido por el SO y
# Electron (protocolo interno app://) se comunica con él; aquí solo se gestiona
# el proceso.
set -u
APP_DIR="\$HOME/.eMailAI"
ELECTRON="\$APP_DIR/app/emailai-electron"
JAR="\$APP_DIR/emailai-backend.jar"
PID_FILE="\$APP_DIR/emailai.pid"
LOG="\$APP_DIR/emailai.log"

cargar_oauth() {
    local cfg="\$APP_DIR/oauth-config.json"
    [ -f "\$cfg" ] || return 0
    command -v python3 >/dev/null || return 0
    eval "\$(python3 - "\$cfg" <<'PY'
import json, sys
try:
    c = json.load(open(sys.argv[1]))
    g = c.get("google", {}); m = c.get("microsoft", {})
    for k, v in {
        "EMAILAI_GOOGLE_CLIENT_ID": g.get("clientId", ""),
        "EMAILAI_GOOGLE_CLIENT_SECRET": g.get("clientSecret", ""),
        "EMAILAI_MICROSOFT_CLIENT_ID": m.get("clientId", ""),
        "EMAILAI_MICROSOFT_CLIENT_SECRET": m.get("clientSecret", ""),
    }.items():
        if v:
            print(k + "=" + repr(v))
except Exception:
    pass
PY
)"
}

corriendo() {
    if [[ -f "\$PID_FILE" ]] && kill -0 "\$(cat "\$PID_FILE")" 2>/dev/null; then
        return 0
    fi
    pgrep -f "emailai[-]electron.*--jar=" >/dev/null 2>&1
}

cmd_start() {
    [[ -x "\$ELECTRON" ]] || { echo "Falta \$ELECTRON — reinstala con lanzadores/linux/sourcecode/install.sh" >&2; exit 1; }
    if corriendo; then
        echo "eMail-IA ya está corriendo"
        exit 0
    fi
    cargar_oauth
    cd "\$APP_DIR" || exit 1   # BD, weka-home y logs quedan dentro de ~/.eMailAI
    setsid nohup "\$ELECTRON" --jar="\$JAR" --no-sandbox >> "\$LOG" 2>&1 &
    echo \$! > "\$PID_FILE"
    echo -n "Abriendo eMail-IA"
    for _ in \$(seq 1 30); do
        corriendo || break
        echo -n "."; sleep 1
    done
    echo
    if corriendo; then
        echo "Listo (log: \$LOG)"
    else
        echo "El proceso terminó pronto; revisa \$LOG" >&2
        exit 1
    fi
}

cmd_stop() {
    local algo=0
    if [[ -f "\$PID_FILE" ]] && kill "\$(cat "\$PID_FILE")" 2>/dev/null; then algo=1; fi
    pkill -f -- "emailai[-]electron.*--jar=" 2>/dev/null && algo=1
    pkill -f -- "java .*emailai[-]backend\.jar" 2>/dev/null && algo=1
    rm -f "\$PID_FILE"
    [[ \$algo -eq 1 ]] && echo "eMail-IA detenido" || echo "No había instancia corriendo"
}

cmd_status() {
    if corriendo; then echo "Corriendo"; else echo "Parado"; exit 1; fi
}

case "\${1:-start}" in
    start)   cmd_start ;;
    stop)    cmd_stop ;;
    restart) cmd_stop; sleep 1; cmd_start ;;
    status)  cmd_status ;;
    log)     tail -n "\${2:-50}" "\$LOG" ;;
    *) echo "Uso: emailai [start|stop|restart|status|log [N]]" >&2; exit 2 ;;
esac
EOF
chmod +x "$BIN_DIR/emailai"
ok "Lanzador creado (comandos: start, stop, restart, status, log)"

# ── Paso 5: icono + entrada de menú KDE ───────────────────────────────
printf '\033[1m[5/6] Integración con el escritorio\033[0m\n'
ICON_SRC="$ROOT/electron/assets/icon-256.png"
ICON_DST="$HOME/.local/share/icons/hicolor/256x256/apps/emailai.png"
mkdir -p "$(dirname "$ICON_DST")"
cp -f "$ICON_SRC" "$ICON_DST"
ok "Icono: $ICON_DST"

DESKTOP="$HOME/.local/share/applications/emailai.desktop"
# Icono por ruta absoluta: la resolución por nombre (Icon=emailai) falla
# a veces en KDE hasta refrescar cachés; la ruta directa siempre funciona.
cat > "$DESKTOP" <<EOF
[Desktop Entry]
Type=Application
Name=eMail-IA
Comment=Cliente de correo con IA
Exec=$BIN_DIR/emailai start
Icon=$ICON_DST
Terminal=false
Categories=Network;Email;
StartupWMClass=eMail-IA
EOF
command -v desktop-file-validate >/dev/null && desktop-file-validate "$DESKTOP"
# Refrescar cachés para que el menú vea la entrada y el icono de inmediato
command -v kbuildsycoca6 >/dev/null && kbuildsycoca6 --noincremental >/dev/null 2>&1 || true
command -v gtk-update-icon-cache >/dev/null && gtk-update-icon-cache -t -f "$(dirname "$(dirname "$ICON_DST")")" >/dev/null 2>&1 || true
command -v update-desktop-database >/dev/null && update-desktop-database "$(dirname "$DESKTOP")" 2>/dev/null || true
ok "Menú de aplicaciones: entrada 'eMail-IA'"

# ── Paso 6: resumen ───────────────────────────────────────────────────
printf '\033[1m[6/6] Comprobando instalación\033[0m\n'
[[ -x "$ELECTRON_DST/emailai-electron" ]] && ok "Ejecutable Electron presente"
[[ -f "$JAR_DST" ]] && ok "Backend Java presente"
[[ -x "$BIN_DIR/emailai" ]] && ok "Lanzador en el PATH"
[[ -f "$DESKTOP" ]] && ok "Entrada de menú válida"

TOTAL="$(du -sh "$APP_DIR" | cut -f1)"
banner "Instalación completada"
printf '  Se ha instalado (%s en total):\n' "$TOTAL"
printf '    • Wrapper Electron + backend → \033[1m%s\033[0m\n' "$APP_DIR"
printf '    • Lanzador                     → \033[1m%s/emailai\033[0m\n' "$BIN_DIR"
printf '    • Menú KDE                     → entrada “eMail-IA”\n\n'
printf '  Para usarla:    emailai start   (o “eMail-IA” en el menú)\n'
printf '  Datos de la app: %s/DB  (todo el flujo queda aquí)\n' "$APP_DIR"
printf '  Desinstalar:    bash lanzadores/linux/sourcecode/uninstall.sh\n'

command -v notify-send >/dev/null && notify-send -i "$ICON_DST" \
    "eMail-IA instalada" "Instalación completada en ~/.eMailAI ($TOTAL). Ejecuta 'emailai start' o úsala desde el menú."
printf '\n'
pass "Listo: ya puedes arrancar eMail-IA"
