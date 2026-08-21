# macOS — DMG (Apple Silicon, arm64)

Genera `emailai-electron-1.0.0-arm64.dmg`: arrastra eMail-IA a Aplicaciones.
Autónomo: Electron + backend.jar + JRE 21 jlink nativo arm64.

## Cómo se construye

**No se puede construir desde Linux** (el JRE jlink no cross-compila SO ni
arquitectura). Dos opciones:

1. **GitHub Actions (recomendado)**: el workflow `release.yml` (job `macos`)
   lo genera en `macos-latest` (arm64). Disparo manual (pestaña Actions) o por tag `v*`.
2. **Mac propia (Apple Silicon)**:

   ```bash
   # Prerequisitos: JDK 21, Maven, Node 22, pnpm
   cd frontend && pnpm install && pnpm build
   cd ../backend && mvn package -DskipTests
   cd ../electron && npm install
   bash build-jre.sh
   npx tsc
   npx electron-builder --mac dmg --arm64
   # → release/emailai-electron-1.0.0-arm64.dmg
   ```

## Limitaciones

- **Solo arm64**: jlink no cross-compila arquitectura. Un dmg x64 (Intel)
  necesitaría runner macos-13 (pendiente si hay demanda).
- **Sin Developer ID / notarización**: al abrir por primera vez, Gatekeeper
  bloqueará ("desarrollador no identificado"). Clic derecho sobre la app →
  *Abrir* → *Abrir*, o `xattr -dr com.apple.quarantine /Applications/eMail-IA.app`.
  La notarización requiere cuenta Apple Developer (99 USD/año).

## Notas técnicas

- Datos en `~/Library/Application Support/emailai-electron/`.
- `hardenedRuntime: true` ya activado en `electron-builder.yml` (requisito
  si algún día se firma/notariza).
