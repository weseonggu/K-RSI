import { createRouter, createWebHistory } from 'vue-router'
import RankingPage from '../pages/RankingPage.vue'
import StockDetailPage from '../pages/StockDetailPage.vue'

const routes = [
  { path: '/', name: 'ranking', component: RankingPage },
  { path: '/stock/:isuCd', name: 'stock-detail', component: StockDetailPage, props: true },
]

export default createRouter({
  // vite base(/rsi/)와 nginx SPA 폴백에 맞춘 히스토리 모드
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})
