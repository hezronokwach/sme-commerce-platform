/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    // In development the browser calls /api on the Vite server, and Vite passes it on to the backend.
    // Same origin for the browser, so no CORS settings are needed locally (like the admin's proxy.conf.json).
    // SSE (the order stream, issue #19) goes through this proxy too.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
  },
})
