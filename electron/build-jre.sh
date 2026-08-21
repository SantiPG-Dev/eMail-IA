#!/usr/bin/env bash
# Monta un JRE dedicado con jlink (Java 21) en electron/jre/.
# electron-builder lo empaqueta como extraResources/jre y main.ts lo usa
# en modo empaquetado → la app NO depende de un Java instalado en el sistema.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$SCRIPT_DIR/jre"

# jlink: del JAVA_HOME si existe, si no del PATH, si no del JVM por defecto
JLINK="$(command -v jlink || true)"
[[ -z "$JLINK" && -x /usr/lib/jvm/default/bin/jlink ]] && JLINK=/usr/lib/jvm/default/bin/jlink
if [[ -z "$JLINK" ]]; then
  if [[ -d "$OUT_DIR" ]]; then
    echo "[build-jre] jlink no disponible; se reutiliza el JRE existente en $OUT_DIR"
    exit 0
  fi
  echo "[build-jre] ERROR: jlink no encontrado y no hay JRE previo en $OUT_DIR" >&2
  exit 1
fi

# java.se cubre todo el JDK estándar (Tomcat/Spring/H2/Weka); los jdk.* extra
# son necesarios para TLS con curvas elípticas (OAuth), charsets de correo
# (ISO-2022-JP etc.), locale español, zipfs (carga del jar) y sun.misc.Unsafe.
MODULES="java.se,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.charsets,jdk.localedata,jdk.zipfs"

echo "[build-jre] Generando JRE con: $JLINK"
rm -rf "$OUT_DIR"
"$JLINK" \
  --add-modules "$MODULES" \
  --output "$OUT_DIR" \
  --strip-debug \
  --no-man-pages \
  --no-header-files \
  --compress=zip-6

"$OUT_DIR/bin/java" -version
du -sh "$OUT_DIR"
echo "[build-jre] OK → $OUT_DIR"
