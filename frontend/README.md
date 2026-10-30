# Frontend — eMail-IA (React)

UI de eMail-IA: **React 19 + Vite 6 + TypeScript 5 + Tailwind CSS**.

## Stack

- **React 19** + Vite 6 + TypeScript
- **Tailwind CSS** (paleta slate+cyan, la misma del JavaFX original)
- **16 temas** con variante dark/light (`data-theme` + `data-mode`)
- Build estático que el backend embebe en el jar (`classpath:/static/`)

## Estructura

```
frontend/src/
├── api/         # cliente axios + SSE
├── components/  # Layout, AccountForm, diálogos, StateViews, ErrorBoundary
├── context/     # Auth, Sync, Theme
├── hooks/       # useAsync
├── pages/       # Correo, Compose, Config, ChatIA, Calendario, Contactos, Tareas, Login
├── test/        # Vitest + Testing Library
├── utils/       # fechas, correo (sanitizado/anti-tracking), error
└── App.tsx      # rutas (Correo por defecto)
```

## Ejecutar y probar

```bash
pnpm install
pnpm dev      # Vite :5173 con proxy /api → :8080
pnpm test     # 80 tests en 15 ficheros
pnpm build    # tsc -b + vite build → dist/
```

## Anti-tracking

`htmlSegunCategoria()` sanitiza el HTML con DOMPurify y bloquea imágenes
remotas (web beacons) salvo en correos `LEGITIMO`; los enlaces se reescriben
a `target=_blank`.
