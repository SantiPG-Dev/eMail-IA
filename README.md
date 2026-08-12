# 📧 eMail-IA

**Cliente de correo de escritorio con IA que cuida tu privacidad.**

> App independiente (no web) — filtro spam que aprende de ti, asistente IA, calendario y tareas. Todo local y cifrado.
> **Estado:** **Release 1.0.0 empaquetada en Fedora 44** (AppImage + RPM + deb). Desarrollo en rama `development`.

---

## 🏗️ Arquitectura del monorepo

eMail-IA está migrando de JavaFX a **Spring Boot + React + Electron**. El repositorio es un monorepo con tres paquetes:

```
eMail-IA/
├── backend/     Spring Boot 3.4 + Java 21 (REST API, correo, Weka, IA, OAuth)
├── frontend/    React 19 + Vite + TypeScript + Tailwind (UI idéntica al JavaFX)
├── electron/    Electron (shell de escritorio, empaqueta backend + frontend)
├── legacy/      Código JavaFX original (referencia visual y lógica)
│   ├── emailAI/     App JavaFX completa (~12k líneas)
│   └── README.md    Documentación del proyecto original
└── .github/     CI/CD
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
| OAuth2 | Google + Microsoft (callback localhost) |
| Seguridad | AES-256-GCM, PBKDF2 600k, BCrypt |

### Decisiones de diseño

- **H2 cifrada** (no PostgreSQL): app desktop single-user, datos locales que no salen del equipo.
- **Electron sidecar**: lanza el backend Spring Boot como proceso hijo y sirve el frontend React.
- **Weka en Java**: el filtro spam es Java-only; el backend se mantiene en Spring Boot/Java.
- **Mismo look & feel**: la UI React replica exactamente el JavaFX original (paleta slate+cyan, 16 temas).

---

## 🚀 Estado de la migración

| Fase | Descripción | Estado |
|------|-------------|--------|
| F0 | Scaffolding monorepo | ✅ |
| F1 | Backend Spring Boot base | ✅ |
| F2 | Dominio (JPA + repos + servicios) | ✅ |
| F3 | REST API + WebSocket | ✅ |
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
# Desarrollo
cd backend && mvn spring-boot:run      # API en localhost:8080
cd frontend && pnpm dev                # Vite :5173 → proxy :8080

# Desktop (Electron)
cd electron && pnpm start              # Lanza backend + abre ventana
```

---

## 📦 Empaquetado 1.0.0 (Linux / Fedora)

Pipeline unificado (backend + frontend + electron TS):

```bash
./scripts/build-package.sh --skip-tests    # JAR 1.0.0 + frontend dist + electron dist
cd electron && npx electron-builder build --linux   # AppImage + .deb
             && bash build-rpm.sh                  # .rpm (spec propio, rpmbuild nativo)
```

Artefactos en `electron/release/`:

| Artefacto | Tamaño | Uso |
|---|---|---|
| `emailai-electron-1.0.0-x86_64.AppImage` | ~181 MB | **Recomendado** — `chmod +x` y ejecutar. Universal, sin instalar. |
| `rpm/RPMS/x86_64/emailai-electron-1.0.0-1.fc44.x86_64.rpm` | ~153 MB | Fedora: `sudo dnf install …rpm` (resuelve `java-21-openjdk` + deps Electron). |
| `emailai-electron-1.0.0-amd64.deb` | ~147 MB | Debian/Ubuntu (requiere `libxcrypt-compat` en sistemas nuevos). |

El backend JAR (`emailai-backend-1.0.0.jar`, 85 MB) se embebe en `resources/backend.jar` dentro de cada artefacto. Requiere Java 21 en el PATH en tiempo de ejecución.

---

## 📚 Más información

- [Plan de migración completo](planes/emailia-migracion-electron.md) (vault Obsidian)
- [Documentación del proyecto original](legacy/README.md) (JavaFX)
- [Código legacy](legacy/emailAI/) (referencia visual y lógica de negocio)

---

<div align="center">

*Proyecto Final de Ciclo — Santiago Pérez Gómez · Málaga, España*

</div>
