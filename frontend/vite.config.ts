/// <reference types="vitest" />
import { defineConfig } from 'vite';
import { createRequire } from 'node:module';
import react from '@vitejs/plugin-react';

const pkg = createRequire(import.meta.url)('./package.json');

export default defineConfig({
  plugins: [react()],
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
  },
  server: {
    port: 5173,
    // Backend de dev: mvn spring-boot:run en backend/ (8080 por defecto)
    proxy: {
      '/api': process.env.EMAILAI_DEV_BACKEND_URL || 'http://localhost:8080',
      '/health': process.env.EMAILAI_DEV_BACKEND_URL || 'http://localhost:8080',
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: false,
  },
});
