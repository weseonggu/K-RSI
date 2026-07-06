import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 개발 시 /api 요청을 api 모듈(8081)로 프록시하여 CORS를 우회한다.
export default defineConfig({
  plugins: [vue()],
  server: {
    // 기본 포트 5173은 Windows(Hyper-V) 예약 포트 범위에 걸릴 수 있어 3000 사용
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
