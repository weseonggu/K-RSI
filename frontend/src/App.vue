<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import RankingTable from './components/RankingTable.vue'
import PaginationBar from './components/PaginationBar.vue'

// ── 조회 조건 ─────────────────────────────────────────
const date = ref('')
const market = ref('KOSPI')
const order = ref('asc') // 기본: 과매도 순 (RSI 오름차순)
const rsiBand = ref('') // '' = 전체, '30-40' 형식
const size = ref(50)
const page = ref(0) // 0-base

// ── 조회 결과 ─────────────────────────────────────────
const items = ref([])
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const loaded = ref(false)
const error = ref('')

// RSI 10단위 구간 옵션 (0~10, 10~20, …, 90~100)
const RSI_BANDS = Array.from({ length: 10 }, (_, i) => ({
  value: `${i * 10}-${(i + 1) * 10}`,
  label: `${i * 10} ~ ${(i + 1) * 10}`,
}))

const countFormat = new Intl.NumberFormat('ko-KR')

const summary = computed(() => {
  if (!loaded.value || totalElements.value === 0) return ''
  return `전체 ${countFormat.format(totalElements.value)}건 · ${totalPages.value}페이지 중 ${page.value + 1}페이지`
})

const showEmpty = computed(
  () => loaded.value && !loading.value && !error.value && items.value.length === 0,
)

// 응답 순서 꼬임 방지용 요청 시퀀스
let requestSeq = 0

async function fetchRanking() {
  if (!date.value) return
  const seq = ++requestSeq
  loading.value = true
  error.value = ''
  try {
    const yyyymmdd = date.value.replaceAll('-', '')
    const params = new URLSearchParams({
      date: yyyymmdd,
      market: market.value,
      order: order.value,
      page: String(page.value),
      size: String(size.value),
    })
    if (rsiBand.value) {
      const [min, max] = rsiBand.value.split('-')
      params.set('rsiMin', min)
      params.set('rsiMax', max)
    }
    // BASE_URL(/rsi/) 기준 상대 경로 — 80(RAG nginx 경유)과 8088(직접) 어디서든 동작
    const res = await fetch(`${import.meta.env.BASE_URL}api/rsi/ranking?${params}`)
    if (seq !== requestSeq) return
    if (!res.ok) {
      const body = await res.json().catch(() => null)
      throw new Error(body?.error ?? `요청 실패 (HTTP ${res.status})`)
    }
    const data = await res.json()
    if (seq !== requestSeq) return
    items.value = data.items ?? []
    totalElements.value = data.totalElements ?? 0
    totalPages.value = data.totalPages ?? 0
  } catch (e) {
    if (seq !== requestSeq) return
    items.value = []
    totalElements.value = 0
    totalPages.value = 0
    error.value = e.message
  } finally {
    if (seq === requestSeq) {
      loading.value = false
      loaded.value = true
    }
  }
}

// 필터/정렬/날짜/건수 변경 → 1페이지부터 재조회
function resetAndFetch() {
  if (page.value !== 0) {
    page.value = 0 // page 워처가 재조회를 수행
  } else {
    fetchRanking()
  }
}

watch([date, market, order, rsiBand, size], resetAndFetch)
watch(page, fetchRanking)

function onPageChange(p) {
  page.value = p
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  // 기본값: 오늘 (로컬 기준) — date 워처가 첫 자동 조회를 수행
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  date.value = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
})
</script>

<template>
  <main class="container">
    <header class="page-head">
      <h1>일별 RSI 순위</h1>
      <p class="subtitle">KOSPI · KOSDAQ 종목의 일별 RSI 순위 조회</p>
    </header>

    <form class="controls" @submit.prevent="fetchRanking">
      <label class="field field-date">
        <span>날짜</span>
        <input v-model="date" type="date" required />
      </label>
      <label class="field">
        <span>시장</span>
        <select v-model="market">
          <option value="KOSPI">KOSPI</option>
          <option value="KOSDAQ">KOSDAQ</option>
        </select>
      </label>
      <label class="field">
        <span>정렬</span>
        <select v-model="order">
          <option value="asc">과매도 순 (RSI 낮은순)</option>
          <option value="desc">과매수 순 (RSI 높은순)</option>
        </select>
      </label>
      <label class="field">
        <span>RSI 구간</span>
        <select v-model="rsiBand">
          <option value="">전체</option>
          <option v-for="band in RSI_BANDS" :key="band.value" :value="band.value">
            {{ band.label }}
          </option>
        </select>
      </label>
      <label class="field">
        <span>표시 건수</span>
        <select v-model.number="size">
          <option :value="20">20개</option>
          <option :value="50">50개</option>
          <option :value="100">100개</option>
        </select>
      </label>
    </form>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-else-if="showEmpty" class="empty">
      해당 날짜에 데이터가 없습니다. (휴장일이거나 아직 수집 전일 수 있습니다)
    </p>

    <div v-if="summary || loading" class="result-meta">
      <span v-if="summary">{{ summary }}</span>
      <span v-if="loading" class="loading-badge">조회 중…</span>
    </div>

    <div v-if="loading && items.length === 0" class="skeleton" aria-hidden="true">
      <div v-for="n in 8" :key="n" class="skeleton-row"></div>
    </div>

    <RankingTable v-if="items.length" :rows="items" :class="{ 'is-loading': loading }" />

    <PaginationBar
      v-if="items.length"
      :page="page"
      :total-pages="totalPages"
      :disabled="loading"
      @change="onPageChange"
    />
  </main>
</template>
