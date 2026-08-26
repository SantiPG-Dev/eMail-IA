# 📧 eMail-IA

**Cliente de correo de escritorio con IA que cuida tu privacidad.**

> App de escritorio (no web) — filtro spam que aprende de ti, asistente IA, calendario y tareas. Todo local y cifrado.
> **Estado:** **Release 1.0.0** con instaladores multiplataforma: Linux (AppImage/deb/rpm/fuente), Windows (NSIS) y macOS (DMG arm64), estos dos últimos vía GitHub Actions. Desarrollo en rama `development`.

---

## 🏗️ Arquitectura del monorepo

eMail-IA está migrando de JavaFX a **Spring Boot + React + Electron**. El repositorio es un monorepo con tres paquetes:

```
eMail-IA/
├── backend/     Spring Boot 3.4 + Java 21 (REST API, correo, Weka, IA, OAuth)
├── frontend/    React 19 + Vite + TypeScript + Tailwind (UI idéntica al JavaFX)
├── electron/    Electron (shell de escritorio, empaqueta backend + frontend + JRE)
├── lanzadores/  Instaladores y scripts de empaquetado multiplataforma
└── .github/     CI/CD (ci.yml: tests; release.yml: instaladores multiplataforma)
```

### Stack

| Capa | Tecnología |
|------|-----------|
| Shell escritorio | Electron + electron-builder |
| Frontend | React 19 + Vite + TypeScript + Tailwind CSS |
| Backend | Spring Boot 3.4.1 + Java 21 |
| Datos | H2 embebida cifrada + Spring Data JPA + Flyway |
| Correo | Jakarta Mail (IMAP/SMTP/XOAUTH2) |
| Spam IA | Weka (Naive Bayes, 100% local) |
| Asistente IA | LangChain4j → LM Studio / OpenAI |
| OAuth2 | Google + Microsoft (loopback RFC 8252) |
| Seguridad | AES-256-GCM, PBKDF2 600k, BCrypt |

### Decisiones de diseño

- **H2 cifrada** (no PostgreSQL): app desktop single-user, datos locales que no salen del equipo.
- **Electron sidecar sin puertos fijos**: el backend arranca en un puerto efímero
  (asignado por el SO en 127.0.0.1) y lo publica en un ready file; el renderer
  carga `app://local/` (protocolo interno de Electron) y el proceso main hace de
  proxy. Cero conflictos de puertos y API inaccesible desde la LAN.
- **Instaladores autosuficientes**: cada artefacto embebe un JRE 21 (jlink,
  ~69 MB) — el usuario no instala Java.
- **Weka en Java**: el filtro spam es Java-only; el backend se mantiene en Spring Boot/Java.
- **Mismo look & feel**: la UI React replica exactamente el JavaFX original (paleta slate+cyan, 16 temas).

---

## 🚀 Estado de la migración

| Fase | Descripción | Estado |
|------|-------------|--------|
| F0 | Scaffolding monorepo | ✅ |
| F1 | Backend Spring Boot base | ✅ |
| F2 | Dominio (JPA + repos + servicios) | ✅ |
| F3 | REST API + JWT stateless | ✅ |
| F4 | Seguridad (OAuth2, JWT persistente, login IMAP) | ✅ |
| F5 | Tests backend | ⏳ |
| F6 | Frontend base (React + Tailwind + 16 temas) | ✅ |
| F7 | Frontend completo (replicar vistas + IA + anti-tracking) | ✅ |
| F8 | Tests frontend | ✅ |
| F9 | Electron wrapper | ✅ |
| F10 | Packaging + CI | ✅ |

---

## 📦 Cómo ejecutar

```bash
# Desarrollo (3 terminales)
cd backend && mvn spring-boot:run      # API en localhost:8080
cd frontend && pnpm dev                # Vite :5173 → proxy :8080
cd electron && npm run dev             # detecta Vite y abre la ventana

# Desktop (Electron) sin Vite: lanza el jar con puerto efímero + app://
cd electron && npm run dev
```

---

## 📥 Instalación

La matriz completa está en [`lanzadores/README.md`](lanzadores/README.md). Resumen:

| Sistema | Qué usar |
|---|---|
| Cualquier Linux | `*.AppImage` — `chmod +x` y ejecutar, sin instalación |
| Debian/Ubuntu | `sudo apt install ./emailai-electron-*.deb` |
| Fedora/RHEL | `sudo dnf install ./emailai-electron-*.rpm` |
| Linux sin root / desde código | `bash lanzadores/linux/sourcecode/install.sh` |
| Windows 10/11 | `*-setup.exe` (NSIS) |
| macOS Apple Silicon | `*.dmg` (sin Developer ID: clic derecho → Abrir la primera vez) |

Descargas: página de **Releases** del repo (el workflow `release.yml` publica
AppImage, deb, rpm, exe y dmg con sus SHA256 en cada tag `v*`).

Los datos del usuario viven siempre en `~/.config/emailai-electron/` (BD H2
cifrada, config, `oauth-config.json`) — desinstalar la app no los borra.

---

## 📦 Empaquetado 1.0.0 (multiplataforma)

Todos los artefactos son **autosuficientes**: Electron + backend.jar + JRE 21
(jlink) — sin Java en el sistema. Ver [`lanzadores/README.md`](lanzadores/README.md)
para la matriz completa (Linux AppImage/deb/rpm/fuente, Windows NSIS, macOS DMG
— estos dos últimos vía GitHub Actions).

Pipeline Linux (backend + frontend + electron TS):

```bash
bash lanzadores/linux/sourcecode/build-package.sh --skip-tests   # JAR 1.0.0 + frontend dist + electron dist
cd electron && npm run dist:linux          # JRE jlink + AppImage + .deb (autosuficientes)
bash ../lanzadores/linux/deb-rpm/build-rpm.sh   # .rpm (spec propio, rpmbuild nativo)
```

Artefactos en `electron/release/`:

| Artefacto | Uso |
|---|---|
| `emailai-electron-1.0.0-x86_64.AppImage` | **Recomendado** — `chmod +x` y ejecutar. Universal, sin instalar. |
| `emailai-electron-1.0.0-amd64.deb` (~191 MB) | Debian/Ubuntu: `sudo apt install ./…deb` |
| `rpm/RPMS/x86_64/*.rpm` | Fedora/RHEL: `sudo dnf install …rpm` |

El backend JAR (`emailai-backend-1.0.0.jar`) y el JRE jlink (~69 MB) se embeben
en `resources/` dentro de cada artefacto.

---

## 📚 Más información

- [Plan de migración completo](planes/emailia-migracion-electron.md) (vault Obsidian)
- Proyecto original en JavaFX (PFC): repositorio separado `PFC - eMail-IA`

---

<div align="center">

*Proyecto Final de Ciclo — Santiago Pérez Gómez · Málaga, España*

</div>
