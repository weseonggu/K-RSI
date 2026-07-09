<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CandleChart from '../components/CandleChart.vue'
import RsiChart from '../components/RsiChart.vue'
import StockHistoryTable from '../components/StockHistoryTable.vue'

const props = defineProps({
  isuCd: { type: String, required: true }, // route param(props:true)
})

const route = useRoute()
const router = useRouter()

// 라우트 쿼리에서 컨텍스트 수신. 딥링크로 name 이 없으면 isuCd 로 대체 표시(계획서 3.2/4.4절).
const market = ref((route.query.market || 'KOSPI').toString().toUpperCase())
const rawDate = ref((route.query.date || '').toString())
const stockName = computed(() => (route.query.name ? route.query.name.toString() : props.isuCd))

// 기간 선택(거래일 수). 초기 120.
const PERIODS = [
  { label: '1개월', days: 22 },
  { label: '3개월', days: 65 },
  { label: '6개월', days: 130 },
  { label: '1년', days: 250 },
]
const days = ref(120)

// ── 조회 상태 ──────────────────────────────────────────
const historyItems = ref([]) // 전체 배열(오름차순)
const loading = ref(false)
const error = ref('')
const loaded = ref(false)

// 차트 가시 구간(logical index range). 최초엔 null → 전체 표시.
const visibleRange = ref(null)

const hasData = computed(() => historyItems.value.length > 0)
const showEmpty = computed(() => loaded.value && !loading.value && !error.value && !hasData.value)

// 가시 구간을 historyItems 슬라이스로 변환(차트 이동 ↔ 하단 목록 동기화, 계획서 4.5절)
const visibleItems = computed(() => {
  const all = historyItems.value
  if (all.length === 0) return []
  let from = 0
  let to = all.length - 1
  if (visibleRange.value) {
    from = Math.max(0, Math.floor(visibleRange.value.from))
    to = Math.min(all.length - 1, Math.ceil(visibleRange.value.to))
  }
  if (from > to) return []
  // 최근 일자가 위로 오도록 내림차순 표시
  return all.slice(from, to + 1).slice().reverse()
})

function todayYmd() {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`
}

let requestSeq = 0

async function fetchHistory() {
  const seq = ++requestSeq
  loading.value = true
  error.value = ''
  try {
    const dateParam = (rawDate.value || todayYmd()).replaceAll('-', '')
    const params = new URLSearchParams({
      market: market.value,
      date: dateParam,
      days: String(days.value),
    })
    const res = await fetch(
      `${import.meta.env.BASE_URL}api/rsi/stock/${encodeURIComponent(props.isuCd)}/history?${params}`,
    )
    if (seq !== requestSeq) return
    if (!res.ok) {
      const body = await res.json().catch(() => null)
      throw new Error(body?.error ?? `요청 실패 (HTTP ${res.status})`)
    }
    const data = await res.json()
    if (seq !== requestSeq) return
    historyItems.value = data.items ?? []
    visibleRange.value = null // 재조회 시 전체 구간으로 초기화
  } catch (e) {
    if (seq !== requestSeq) return
    historyItems.value = []
    error.value = e.message
  } finally {
    if (seq === requestSeq) {
      loading.value = false
      loaded.value = true
    }
  }
}

function onVisibleRangeChange(range) {
  visibleRange.value = range
}

function goBack() {
  router.back()
}

watch(days, fetchHistory)
onMounted(fetchHistory)
</script>

<template>
  <main class="container detail">
    <header class="detail-head">
      <button class="back-btn" type="button" @click="goBack">← 목록</button>
      <div class="detail-title">
        <h1>{{ stockName }}</h1>
        <p class="subtitle">{{ isuCd }} · {{ market }}</p>
      </div>
      <div class="period-select">
        <button
          v-for="p in PERIODS"
          :key="p.days"
          type="button"
          class="period-btn"
          :class="{ active: days === p.days }"
          @click="days = p.days"
        >
          {{ p.label }}
        </button>
      </div>
    </header>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-else-if="showEmpty" class="empty">
      표시할 시세 데이터가 없습니다. (미상장·거래정지이거나 아직 수집 전일 수 있습니다)
    </p>

    <div v-if="loading" class="result-meta">
      <span class="loading-badge">조회 중…</span>
    </div>

    <template v-if="hasData">
      <section class="chart-stack">
        <CandleChart :items="historyItems" @visible-range-change="onVisibleRangeChange" />
        <RsiChart :items="historyItems" :visible-range="visibleRange" />
      </section>

      <h2 class="section-title">일별 시세</h2>
      <StockHistoryTable :rows="visibleItems" />
    </template>
  </main>
</template>
