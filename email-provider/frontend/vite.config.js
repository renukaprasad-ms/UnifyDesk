import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/console/v1': { target: 'http://127.0.0.1:18012', changeOrigin: true },
    },
  },
})
