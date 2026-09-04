# Características y consumo de recursos — eMail-IA 1.3.0

Medido en vivo el 04-09-2026 sobre la instalación rpm en Fedora 44 (KDE),
Ryzen 12 núcleos / 30 GB RAM, con la app en uso (cuenta cargada, ~16 min de
sesión, reposo). Fuente: `ps`, `/proc/*/smaps_rollup` (PSS = memoria real,
la compartida cuenta una vez), `du`, `rpm -q`.

## Disco

| Qué | Tamaño |
| --- | --- |
| Instalación (`/opt/eMail-IA`) | **419 MB** |
| → backend.jar (Spring Boot + Weka) | 85 MB |
| → JRE 21 dedicado (jlink) | 74 MB |
| → Electron/Chromium + app.asar | ~260 MB |
| RPM instalado (según dnf) | 438 MB |
| RPM descargable (comprimido xz) | 199 MB |
| Datos de usuario (`~/.config/emailai-electron/`) | **240 MB** |
| → BD H2 cifrada (`DB/`) | 238 MB |

Autosuficiente: no requiere Java ni nada instalado. Desinstalar no borra
`~/.config/` (los 240 MB de datos sobreviven).

## Memoria (en uso)

| Proceso | PSS |
| --- | --- |
| Backend Java (JVM + H2 + Weka) | **1.233 MB** |
| Electron ×9 (main, gpu, renderers, red…) | ~455 MB |
| **Total app** | **≈1,7 GB** |
| **Total con heap capado** (1.3.0-2) | **877 MB** |

Contexto: la máquina tiene 30 GB (uso total del sistema: 5,6 GB).

**Corregido en 1.3.0-2**: el backend arrancaba **sin `-Xmx`** → heap máximo
por defecto = 25% de la RAM (8 GB aquí) y ~470 MB de heap inicial. Ahora
el spawn lleva `-Xms64m -Xmx768m` (perilla: env `EMAILAI_XMX` para subirla
si Weka/H2 la piden). Medido tras el cap: **JVM 525 MB (antes 1.233) y
total 877 MB — un 48% menos**.

## CPU

| Estado | Consumo |
| --- | --- |
| Reposo (idle IMAP, sin interactuar) | **~9% de 1 núcleo** (≈0,75% del equipo) |
| Reparto | JVM ~4,7% · GPU ~2% · renderer ~1,8% |

Constante pero bajo: el sync IMAP y el polling no paran del todo, pero en
un equipo de 12 núcleos es despreciable.

## Procesos e hilos

- **10 procesos**: 1 JVM (backend) + 9 de Electron/Chromium (main, gpu,
  zygote ×3, network, broker, 2 renderers)
- **~150 hilos** en total (la mayoría del JVM)

## Arranque

- Backend listo (puerto efímero + ready file) en **~5 s** desde el lanzamiento
- Sin puertos fijos: API en 127.0.0.1 puerto aleatorio, inaccesible desde la red

## Resumen en una línea

419 MB en disco + 240 MB de datos, ~0,9 GB RAM con heap capado (1.3.0-2;
era ~1,7 GB sin cap), <10% de un núcleo en reposo, ~5 s en arrancar.
