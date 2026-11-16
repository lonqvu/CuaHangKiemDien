import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Chi can doc bien moi truong; khong cai them @types/node
declare const process: { env: Record<string, string | undefined> }

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: { '/api': { target: process.env.API_TARGET || 'http://localhost:8090', changeOrigin: true } },
  },
})
