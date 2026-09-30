/// <reference types="vitest/config" />
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      manifest: {
        name: "LinguaLoop",
        short_name: "LinguaLoop",
        description: "Language learning with spaced repetition",
        theme_color: "#123b63",
        background_color: "#f7f9fb",
        display: "standalone",
        start_url: "/",
      },
      workbox: {
        globPatterns: ["**/*.{js,css,html,svg,png,ico}"],
        runtimeCaching: [
          {
            // Content JSON (languages, units, lessons): network-first so the
            // most recent lesson data wins, cached for offline study.
            urlPattern: /\/api\/(languages|units|lessons|audio\/assets)/,
            handler: "NetworkFirst",
            options: {
              cacheName: "api-content",
              networkTimeoutSeconds: 3,
              expiration: { maxEntries: 80, maxAgeSeconds: 7 * 24 * 3600 },
              cacheableResponse: { statuses: [200] },
            },
          },
          {
            // Audio assets are immutable (content-addressed): cache-first.
            urlPattern: /\/api\/audio\//,
            handler: "CacheFirst",
            options: {
              cacheName: "audio-files",
              expiration: { maxEntries: 200, maxAgeSeconds: 365 * 24 * 3600 },
              cacheableResponse: { statuses: [200, 206] },
            },
          },
          {
            urlPattern: /\/api\/learners\/me\/(queue|stats)/,
            handler: "NetworkFirst",
            options: {
              cacheName: "api-learner",
              networkTimeoutSeconds: 3,
              expiration: { maxEntries: 20, maxAgeSeconds: 3600 },
              cacheableResponse: { statuses: [200] },
            },
          },
          {
            // Mutations must reach the server; the offline queue handles failures.
            urlPattern: /\/api\/(sessions|auth)/,
            handler: "NetworkOnly",
          },
        ],
      },
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: false,
  },
});
