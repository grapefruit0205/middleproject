import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      workbox: {
        // The legacy SPA must not replace Daylight or API navigations with its index.html.
        navigateFallbackDenylist: [/^\/daylight(?:\/|$)/, /^\/api(?:\/|$)/, /^\/healthz(?:\/|$)/],
      },
      includeAssets: ['favicon.svg'],
      manifest: {
        name: 'Reminder Platform',
        short_name: 'Reminder',
        description: 'Reminder Platform local foundation',
        theme_color: '#006bff',
        background_color: '#f7faff',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
        ],
      },
    }),
  ],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
})
