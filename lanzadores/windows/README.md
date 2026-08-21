# Windows — instalador NSIS (x64)

Genera `emailai-electron-1.0.0-setup.exe`: instalador clásico de Windows con
atajo en menú Inicio y desinstalador. Autónomo: Electron + backend.jar +
JRE 21 jlink (no instala Java).

## Cómo se construye

**No se puede construir desde Linux** (el JRE jlink no cross-compila SO).
Dos opciones:

1. **GitHub Actions (recomendado)**: el workflow `release.yml` (job `windows`)
   lo genera en `windows-latest`. Disparo manual (pestaña Actions) o por tag `v*`.
2. **Máquina Windows propia**:

   ```powershell
   # Prerequisitos: JDK 21, Maven, Node 22, pnpm
   cd frontend ; pnpm install ; pnpm build
   cd ..\backend ; mvn package -DskipTests
   cd ..\electron ; npm install
   bash build-jre.sh        # git-bash (viene con Git para Windows)
   npx tsc
   npx electron-builder --win nsis
   # → release\emailai-electron-1.0.0-setup.exe
   ```

## Notas técnicas

- `findJava()` de `main.ts` resuelve `resources\jre\bin\java.exe` empaquetado.
- Datos en `%APPDATA%\emailai-electron\` (BD, config, oauth-config.json).
- OAuth: el callback usa `http://localhost:9876`; el firewall de Windows puede
  preguntar al primer uso — es loopback local, aceptar.
