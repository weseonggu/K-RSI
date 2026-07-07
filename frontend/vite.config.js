import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 앱은 /rsi/ 서브패스 기준으로 동작한다 — 운영에서 RAG nginx(80)가 /rsi/ 를 프록시하므로
// 80 경유와 8088 직접 접속이 같은 경로 체계를 쓴다.
// 개발 시 /rsi/api 요청을 api 모듈(8081)의 /api 로 프록시하여 CORS를 우회한다.
export default defineConfig({
  plugins: [vue()],
  base: '/rsi/',
  server: {
    // 기본 포트 5173은 Windows(Hyper-V) 예약 포트 범위에 걸릴 수 있어 3000 사용
    port: 3000,
    proxy: {
      '/rsi/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/rsi\/api/, '/api'),
      },
    },
  },
})
