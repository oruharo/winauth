import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        // 環境変数でサーバーを切り替え可能
        target: process.env.VITE_API_URL || 'http://localhost:8082',
        changeOrigin: true,
        configure: (proxy, options) => {
          // 認証ヘッダーがある場合は転送
          proxy.on('proxyReq', (proxyReq, req, res) => {
            if (req.headers.authorization) {
              proxyReq.setHeader('Authorization', req.headers.authorization);
            }
          });
        },
      },
    },
  },
})