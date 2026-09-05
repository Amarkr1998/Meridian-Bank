/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: false,
    // This workstation's user profile path contains non-ASCII characters, which breaks Vitest's
    // default `forks` pool (separate OS processes — same class of issue as the JDK/Mockito
    // self-attach failure documented in services/api-gateway/README.md). `threads` runs tests in
    // worker_threads within this same Node process instead, sidestepping the broken path/IPC path.
    pool: 'threads',
  },
})
