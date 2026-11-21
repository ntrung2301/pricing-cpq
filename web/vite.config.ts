/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      // S3-T8 (docs/decisions.md D-049): measure-canvas.html is a second,
      // independent multi-page entry (Vite does not auto-discover extra
      // .html files during `vite build` — only index.html by default), so
      // it must be listed explicitly to be included in `npm run build`.
      input: {
        main: fileURLToPath(new URL('./index.html', import.meta.url)),
        measureCanvas: fileURLToPath(new URL('./measure-canvas.html', import.meta.url)),
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    testTimeout: 15000,
  },
})
