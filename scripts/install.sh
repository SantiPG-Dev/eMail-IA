#!/usr/bin/env bash
# ============================================================
# eMail-IA — Instalación local sin sudo en ~/.eMailAI
# ============================================================
# Instala el programa (jar con backend + frontend embebido) en
# ~/.eMailAI y deja todo el flujo de la app dentro de esa carpeta:
# BD H2, modelos Weka, jwt.key, logs y configuración OAuth.
#
# Crea:
#   ~/.eMailAI/emailai-backend.jar      el programa
#   ~/.eMailAI/oauth-config.json        credenciales OAuth (opcional)
#   ~/.eMailAI/DB/                      datos en runtime (se crean solos)
#   ~/.local/bin/emailai                ejecutable en el PATH
#   ~/.local/share/applications/        entrada de menú KDE
#   ~/.local/share/icons/hicolor/       icono
#
# Uso:  bash scripts/install.sh          (desde la raíz del repo)
# Desinstalar:
#   emailai stop
#   rm -rf ~/.eMailAI ~/.local/bin/emailai \
#          ~/.local/share/applications/emailai.desktop \
#          ~/.local/share/icons/hicolor/*/apps/emailai.png
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$HOME/.eMailAI"
BIN_DIR="$HOME/.local/bin"
JAR_SRC="$ROOT/backend/target/emailai-backend-1.0.0.jar"
JAR_DST="$APP_DIR/emailai-backend.jar"
PORT=8420

msg()  { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
err()  { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# --- 1) Verificar el jar ------------------------------------------------
if [[ ! -f "$JAR_SRC" ]]; then
    err "No existe $JAR_SRC
Compila primero:  cd frontend && pnpm build && cd ../backend && mvn package -DskipTests"
fi
msg "Jar encontrado: $JAR_SRC ($(du -h "$JAR_SRC" | cut -f1))"

# --- 2) Copiar programa a ~/.eMailAI -----------------------------------
mkdir -p "$APP_DIR" "$BIN_DIR"
cp -f "$JAR_SRC" "$JAR_DST"
msg "Programa instalado en $JAR_DST"

# --- 3) Config OAuth (si existe en electron/, se reutiliza) ------------
OAUTH_SRC="$ROOT/electron/oauth-config.json"
if [[ ! -f "$APP_DIR/oauth-config.json" ]]; then
    if [[ -f "$OAUTH_SRC" ]]; then
        cp "$OAUTH_SRC" "$APP_DIR/oauth-config.json"
        msg "oauth-config.json copiado de electron/"
    else
        cat > "$APP_DIR/oauth-config.json" <<'EOF'
{
  "google": { "clientId": "", "clientSecret": "" },
  "microsoft": { "clientId": "", "clientSecret": "" }
}
EOF
        msg "oauth-config.json plantilla creado (rellénalo para OAuth)"
    fi
else
    msg "oauth-config.json existente conservado"
fi

# --- 4) Lanzador ~/.local/bin/emailai ----------------------------------
cat > "$BIN_DIR/emailai" <<EOF
#!/usr/bin/env bash
# eMail-IA — lanzador de la instalación local (~/.eMailAI)
set -u
APP_DIR="\$HOME/.eMailAI"
JAR="\$APP_DIR/emailai-backend.jar"
PID_FILE="\$APP_DIR/emailai.pid"
LOG="\$APP_DIR/emailai.log"
PORT=$PORT
URL="http://localhost:\$PORT"
HEALTH="\$URL/health"

cargar_oauth() {
    local cfg="\$APP_DIR/oauth-config.json"
    [ -f "\$cfg" ] && command -v python3 >/dev/null || return 0
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
    curl -sf "\$HEALTH" >/dev/null 2>&1
}

cmd_start() {
    if corriendo; then
        echo "eMail-IA ya está corriendo en \$URL"
    else
        cargar_oauth
        cd "\$APP_DIR" || exit 1
        # setsid: sesión propia → sobrevive al cierre del terminal (fallback a nohup)
        if command -v setsid >/dev/null; then
            setsid nohup java -jar "\$JAR" \\
                --server.port=\$PORT \\
                --emailai.data-dir=DB \\
                >> "\$LOG" 2>&1 &
        else
            nohup java -jar "\$JAR" \\
                --server.port=\$PORT \\
                --emailai.data-dir=DB \\
                >> "\$LOG" 2>&1 &
        fi
        echo \$! > "\$PID_FILE"
        echo -n "Arrancando eMail-IA"
        for _ in \$(seq 1 60); do
            corriendo && break
            echo -n "."; sleep 1
        done
        echo
        if corriendo; then
            echo "eMail-IA listo en \$URL (log: \$LOG)"
        else
            echo "No arrancó en 60s — mira \$LOG" >&2
            exit 1
        fi
    fi
    command -v xdg-open >/dev/null && xdg-open "\$URL" >/dev/null 2>&1 || true
}

cmd_stop() {
    # El patrón con [-] evita que pkill se mate a sí mismo o a procesos
    # ajenos cuyo cmdline contenga el patrón (editores, greps, shells).
    if [[ -f "\$PID_FILE" ]] && kill "\$(cat "\$PID_FILE")" 2>/dev/null; then
        echo "eMail-IA detenido"
    elif pkill -f -- "java .*emailai[-]backend\.jar" 2>/dev/null; then
        echo "eMail-IA detenido"
    else
        echo "No había instancia corriendo"
    fi
    rm -f "\$PID_FILE"
}

cmd_status() {
    if corriendo; then
        echo "Corriendo en \$URL"
    else
        echo "Parado"
        exit 1
    fi
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
msg "Lanzador instalado: $BIN_DIR/emailai"

# --- 5) Icono + entrada de menú KDE -------------------------------------
ICON_SRC="$ROOT/electron/assets/icon-256.png"
ICON_DST="$HOME/.local/share/icons/hicolor/256x256/apps/emailai.png"
mkdir -p "$(dirname "$ICON_DST")"
cp -f "$ICON_SRC" "$ICON_DST"

DESKTOP="$HOME/.local/share/applications/emailai.desktop"
cat > "$DESKTOP" <<EOF
[Desktop Entry]
Type=Application
Name=eMail-IA
Comment=Cliente de correo con IA — instalación local en ~/.eMailAI
Exec=$BIN_DIR/emailai start
Icon=emailai
Terminal=false
Categories=Network;Email;
StartupWMClass=emailai
EOF
command -v desktop-file-validate >/dev/null && desktop-file-validate "$DESKTOP"
command -v update-desktop-database >/dev/null && update-desktop-database "$(dirname "$DESKTOP")" 2>/dev/null || true
msg "Menú KDE: entrada 'eMail-IA' creada"

msg "Instalación completada: ejecuta 'emailai start' (o el menú KDE)"
