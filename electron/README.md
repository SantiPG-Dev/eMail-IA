# Electron — eMail-IA (shell de escritorio)

Wrapper **Electron** que envuelve la app de escritorio.

## Arquitectura

```
Electron (main process)
├─ spawn: java -jar backend.jar --server.port=8420
├─ health-check: poll http://localhost:8420/health
├─ BrowserWindow → http://localhost:8420 (frontend React servido por backend)
├─ tray icon + notificaciones
└─ on-quit → kill proceso backend
```

## Requisitos

- Java 21+ (para el backend JAR)
- Node.js 22+

## Desarrollo

```bash
# 1. Construir el backend JAR
cd ../backend
mvn clean package -DskipTests

# 2. Compilar el Electron
cd ../electron
npm install
npm run build       # Compila TypeScript → dist/

# 3. Ejecutar (busca el JAR en backend/target/)
npm start
```

## Estructura

```
electron/
├── src/
│   ├── main.ts        # Proceso principal: spawn backend, ventana, lifecycle
│   └── preload.ts     # IPC seguro (contextBridge)
├── assets/
│   ├── icon-32.png
│   └── icon-256.png
├── dist/              # Compilado TypeScript (main.js, preload.js)
├── package.json
└── tsconfig.json
```
