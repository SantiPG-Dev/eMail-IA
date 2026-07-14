# Frontend — eMail-IA (React)

Frontend de eMail-IA migrado a **React 19 + Vite + TypeScript + Tailwind CSS**.

## Stack previsto (Fase 6+)

- **React 19** + Vite + TypeScript
- **Tailwind CSS** (paleta slate+cyan idéntica al JavaFX original)
- **Sistema de 16 temas** (CSS variables + `data-theme`/`data-mode`)
- Build estático servido por el backend Spring Boot

## Replicación visual

El objetivo es que la UI se vea **idéntica** al JavaFX actual (en `legacy/emailAI/src/main/resources/`).

### Paleta (tema emailIA oscuro, por defecto)

| Token | Valor | Tailwind |
|-------|-------|----------|
| Fondo app | `#0F172A` | slate-900 |
| Fondo sidebar | `#0B1220` | custom |
| Card/panel | `#1E293B` | slate-800 |
| Elevado | `#334155` | slate-700 |
| Texto | `#F1F5F9` | slate-100 |
| Secundario | `#94A3B8` | slate-400 |
| Acento | `#22D3EE` | cyan-400 |
| Selección | `#38BDF8` | sky-400 |
| Glow IA | `#00F0FF` | cyan glow |

### Vistas a replicar

| JavaFX (legacy) | React |
|-----------------|-------|
| `main-view.fxml` | `Layout.tsx` (sidebar + Outlet) |
| `correo-view.fxml` | `CorreoPage.tsx` (split-pane + iframe HTML) |
| `login-view.fxml` | `LoginPage.tsx` |
| `chatIA-view.fxml` | `ChatIAPage.tsx` |
| `compose-view.fxml` | `ComposePage.tsx` |
| `config-view.fxml` | `ConfigPage.tsx` |
| `calendario-view.fxml` | `CalendarioPage.tsx` |
| `contactos-view.fxml` | `ContactosPage.tsx` |
| `tareas-view.fxml` | `TareasPage.tsx` |

> Fase 0 completada — scaffolding. La implementación empieza en Fase 6.
