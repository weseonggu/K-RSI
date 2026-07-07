<script setup>
import { ref, onMounted } from 'vue'
import RankingTable from './components/RankingTable.vue'

const date = ref('')
const market = ref('KOSPI')
const order = ref('desc')
const limit = ref(50)

const rows = ref([])
const loading = ref(false)
const error = ref('')

async function fetchRanking() {
  if (!date.value) {
    error.value = '날짜를 선택하세요.'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const yyyymmdd = date.value.replaceAll('-', '')
    const params = new URLSearchParams({
      date: yyyymmdd,
      market: market.value,
      order: order.value,
      limit: String(limit.value),
    })
    // BASE_URL(/rsi/) 기준 상대 경로 — 80(RAG nginx 경유)과 8088(직접) 어디서든 동작
    const res = await fetch(`${import.meta.env.BASE_URL}api/rsi/ranking?${params}`)
    if (!res.ok) {
      const body = await res.json().catch(() => null)
      throw new Error(body?.error ?? `요청 실패 (HTTP ${res.status})`)
    }
    rows.value = await res.json()
    if (rows.value.length === 0) {
      error.value = '해당 날짜에 데이터가 없습니다. (휴장일이거나 아직 수집 전일 수 있습니다)'
    }
  } catch (e) {
    rows.value = []
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 기본값: 오늘 (로컬 기준)
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  date.value = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
})
</script>

<template>
  <main class="container">
    <h1>일별 RSI 순위</h1>

    <form class="controls" @submit.prevent="fetchRanking">
      <label>
        날짜
        <input v-model="date" type="date" required />
      </label>
      <label>
        시장
        <select v-model="market">
          <option value="KOSPI">KOSPI</option>
          <option value="KOSDAQ">KOSDAQ</option>
        </select>
      </label>
      <label>
        정렬
        <select v-model="order">
          <option value="desc">과매수 순 (RSI 높은순)</option>
          <option value="asc">과매도 순 (RSI 낮은순)</option>
        </select>
      </label>
      <label>
        건수
        <input v-model.number="limit" type="number" min="1" max="500" />
      </label>
      <button type="submit" :disabled="loading">{{ loading ? '조회 중…' : '조회' }}</button>
    </form>

    <p v-if="error" class="error">{{ error }}</p>

    <RankingTable v-if="rows.length" :rows="rows" />
  </main>
</template>
