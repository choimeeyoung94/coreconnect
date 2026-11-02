import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import { fileURLToPath } from 'url'

// ESM에서 __dirname을 사용하기 위한 설정
const __dirname = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  plugins: [react()],
  resolve: {
    extensions: ['.js', '.jsx', '.ts', '.tsx', '.json'],
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: undefined,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
    testTimeout: 30000,
    include: ['src/**/*.test.{js,jsx}'],
  },
  server: {
    proxy: {
      '/api/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      // 웹소켓 핸드셰이크 등 프록시 (반드시 ws: true 추가!)
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
        secure: false,
      },
    },
  },
})