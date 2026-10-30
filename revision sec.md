# Revision de seguridad — stack de eMail-IA

Fecha: 29-10-2026. Version revisada: 1.3.3. Version corregida: 1.3.4.

Metodo: `npm audit` / `pnpm audit` sobre los arboles reales del frontend y de
Electron, y consulta a la API de OSV (`querybatch`, ecosistema Maven) sobre las
93 dependencias resueltas del backend (`mvn dependency:list`). Se reviso tambien
a mano el codigo de seguridad propio (JWT, CORS, CSP, host, Electron, correo).

## Resumen

| Parte | Antes | Despues (1.3.4) |
|---|---|---|
| Frontend (`pnpm audit`) | 4 criticos, 16 altos, 18 moderados, 4 bajos | 0 |
| Electron (`npm audit`) | 1 critico, 17 altos | 0 |
| Backend (OSV sobre 93 artefactos) | 17 artefactos con avisos (Tomcat 30, spring-webmvc 14, jackson-databind 13, ...) | 1 artefacto con avisos: `spring-webmvc 6.2.19` |

## Corregido

### Backend

- **Spring Boot 3.4.1 → 3.5.16.** Arrastra Spring Framework 6.2.19, Spring Security
  6.5.x, Hibernate 6.6 y Flyway 11. Corrige de golpe los avisos de
  `spring-security-*`, `spring-data-commons`, `spring-expression`, `spring-core`,
  `logback-core` y buena parte de Tomcat y Jackson.
- **Tomcat 10.1.34 → 10.1.61** (propiedad `tomcat.version`). Corrige, entre otros:
  - GHSA-gcx9-497g-6cp6, GHSA-h3x4-894j-xpx5, GHSA-9xv2-5v5q-p794 (criticos),
  - GHSA-f525-44xv-f2qj (critico, bypass de validacion SNI en HTTP/2),
  - y el resto de avisos de `tomcat-embed-core` publicados hasta 10.1.61.
- **Jackson 2.18.2 → 2.21.8** (propiedad `jackson-bom.version`). Corrige
  GHSA-7hhh-6rmp-j9qf y GHSA-p6pp-m3f8-5c89 (ReDoS y crecimiento sin limite en
  `jackson-core`) y GHSA-wv8q-qhhj-9h54, GHSA-5gvw-p9qm-jgwh y compania en
  `jackson-databind`.
- **Log4j 2.24.3 → 2.26.1** (propiedad `log4j2.version`). Corrige
  GHSA-qv9r-c865-cp47 (codificacion incorrecta de no finitos en `MapMessage`).
- **jakarta.mail 2.0.1 → 2.0.2** y **jjwt 0.12.6 → 0.12.7** (parches de la misma
  linea).

Nota: las propiedades `tomcat.version`, `jackson-bom.version` y `log4j2.version`
estan por delante de lo que fija el BOM de 3.5.16 porque los avisos salieron
despues del ultimo patch de esa linea. Revisar en cada subida de Spring Boot.

### Frontend

- **axios 1.18.1 → 1.21.0.** Corrige GHSA-c29m-xwm3-cm6r, GHSA-mghh-pgcx-3jjj,
  GHSA-3pq3-5fj3-cg6v, GHSA-542g-h47m-68v8, GHSA-r4gj-5m52-g5wh,
  GHSA-x97p-jq2g-jp4f, GHSA-m8m8-qj5v-23w3 y los moderados de polucion de
  prototipo y de cabeceras.
- **DOMPurify 3.4.13 → 3.4.17.** Corrige GHSA-p98j-92pf-mc4p, GHSA-6688-9rhm-gjv2
  y el critico de la serie 3.4.x (`IN_PLACE` con subarbol desconectado). Es la
  capa que sanea el HTML de los correos antes de pintarlo en el iframe.
- **react-router-dom 7.18.1 → 7.18.5.** Corrige GHSA-qwww-vcr4-c8h2 (CSRF en modo
  RSC; aqui no se usa, pero se alinea).
- **vite 6.4.3 → 6.4.5** y **postcss 8.5.18 → 8.5.32.** Corrigen los avisos de
  traversal en el servidor de desarrollo y de lectura de `.map`; solo afectan a
  desarrollo, pero el servidor de Vite escucha en local.
- **vitest 2.1.9 → 4.1.12.** Corrige el critico GHSA-5xrq-8626-4rwp (lectura y
  ejecucion arbitraria de ficheros con la UI de Vitest escuchando) y
  GHSA-82fw-gwwq-j7x9. Solo herramientas de test.

### Electron

- **electron 33.4.11 → 45.1.0.** Cierra la lista completa de avisos del runtime
  (ASAR integrity bypass, escapes de sandbox en iframes, *use-after-free* en
  callbacks de permisos, inyeccion de `commandLineSwitches`, robo de origen en
  los manejadores de permisos, etc.) y trae un Chromium bastante mas nuevo.
- **electron-builder 25.1.8 → 26.18.0.** Cierra `tar` (critico), `node-gyp`,
  `cacache`, `app-builder-lib` y demas dependencias de empaquetado.

Verificado con la app empaquetada real: arranca como 1.3.4, el backend responde
`/health` y `/api/status`, el renderer pide `/api/cuentas`, `/api/mensajes` y el
sync, y el proxy `app://` sigue inyectando la CSP.

## Pendiente (no se puede arreglar sin saltar de version mayor)

- **Spring Framework 6.2.19: GHSA-j9f9-w8pj-32f8 y GHSA-pc63-qcmh-9cmg**
  (criticos, ambos de `spring-webmvc`). El arreglo solo existe en Spring
  Framework 7.0.9, es decir, en **Spring Boot 4.1.x**; la linea 6.2 ya no recibe
  parches. Riesgo real en esta app:
  - `GHSA-pc63-qcmh-9cmg` es de `XsltView`; el proyecto no usa vistas XSLT.
  - `GHSA-j9f9-w8pj-32f8` es corrupcion de un stream SSE al renderizar
    fragmentos; el proyecto usa `SseEmitter` con JSON, no fragmentos de vista.
  Aun asi, conviene planificar la migracion a Spring Boot 4.1.2 (Spring
  Framework 7, Jackson 3, Jakarta EE 11). No es un cambio de parche: toca
  imports de Jackson, seguridad y propiedades.
- **`.deb` en local.** `electron-builder` usa `fpm` con Ruby, que necesita
  `libcrypt.so.1` y no existe en Fedora 44. El `.deb` sale en el CI (Ubuntu); el
  AppImage y el RPM se generan en local.
- **Dependencias solo de desarrollo dentro del frontend.** `esbuild`, `nanoid`,
  `source-map-js` y `browserslist` quedan resueltos por las subidas de vite y
  vitest; si `pnpm audit` vuelve a marcarlos, se fijan con `pnpm.overrides`.

## Revisado y sin cambios (ya estaba bien)

- **Host header**: el filtro JWT rechaza cualquier `Host` que no sea
  `localhost`/`127.0.0.1`/`[::1]`, contra DNS rebinding.
- **CORS**: solo `http://localhost:5173` y `http://127.0.0.1:5173` (Vite dev);
  `file://*` retirado.
- **Autorizacion**: `anyRequest().denyAll()` por defecto; solo `/health`,
  `/api/auth/**`, `/api/status` y el setup inicial son publicos.
- **JWT**: clave de 512 bits generada con `SecureRandom` y persistida en
  `<data-dir>/jwt.key` con permisos 600; sin clave por defecto en el codigo.
- **Login**: `LoginRateLimiter` (5 intentos por email, 20/minuto global).
- **CSP**: `script-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`,
  inyectada por el proxy Electron; el iframe del correo lleva su propia CSP.
- **HTML de correos**: `sandbox` en el iframe + DOMPurify + CSP.
- **Electron**: `nodeIntegration: false`, `contextIsolation: true`, sandbox por
  defecto, `will-navigate` y `setWindowOpenHandler` con lista blanca,
  `shell.openExternal` solo para http/https.
- **Debug**: `DebugController` solo se carga con `emailai.debug.enabled=true`.
- **Ficheros**: el backend no escribe adjuntos con nombres del usuario.

## Sugerencias para la siguiente revision

1. Migrar a Spring Boot 4.1.x para cerrar los dos avisos de Spring.
2. Anadir a CI un paso `pnpm audit --production` y una consulta a OSV del arbol
   Maven, para que un CVE nuevo falle el build.
3. `GET /api/cuentas` es publico (la pantalla de login necesita listar cuentas):
   expone los emails configurados a cualquier proceso local. Valorar devolver
   solo el email y el id, o exigir un token de setup.
4. El JWT dura 30 dias y no se puede revocar: valorar lista de revocacion ligera
   o invalidar al cambiar la contraseña.
5. `JwtService.ensureLength` rellena con ceros una clave corta de entorno; es
   mejor rechazar secretos de menos de 32 bytes que rellenarlos.
6. `IcsService.exportarCalendarioAICS(String rutaArchivo)` es codigo muerto que
   escribiria en una ruta arbitraria si alguien lo conecta a una peticion.
   Borrarlo.
7. `GET /api/cuentas` y `POST /api/cuentas` en modo setup: revisar que el setup
   inicial no se pueda repetir mientras haya cuentas (hoy ya se exige JWT).
