import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// Vite 配置：开发服务器代理、路径别名
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // @ 指向 src 目录，便于模块引用
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      // 所有 /api 请求代理到后端 Spring Boot 服务
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        headers: {
          'Content-Type': 'application/json;charset=UTF-8'
        }
      }
    }
  }
})
