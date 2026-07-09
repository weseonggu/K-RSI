<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { createChart, CandlestickSeries, HistogramSeries, ColorType } from 'lightweight-charts'

const props = defineProps({
  items: { type: Array, required: true }, // 오름차순 StockHistoryItem 배열
})

const emit = defineEmits(['visible-range-change'])

const UP = '#dc2626' // 상승: 빨강 (한국 관례)
const DOWN = '#2563eb' // 하락: 파랑

const containerRef = ref(null)
let chart = null
let candleSeries = null
let volumeSeries = null
let resizeObserver = null

function toCandleData(items) {
  return items.map((it) => ({
    time: it.date,
    open: it.openPrice,
    high: it.highPrice,
    low: it.lowPrice,
    close: it.closePrice,
  }))
}

function toVolumeData(items) {
  return items.map((it) => ({
    time: it.date,
    value: it.volume,
    color: it.closePrice >= it.openPrice ? UP : DOWN,
  }))
}

function applyData() {
  if (!candleSeries || !volumeSeries) return
  candleSeries.setData(toCandleData(props.items))
  volumeSeries.setData(toVolumeData(props.items))
  chart.timeScale().fitContent()
}

onMounted(() => {
  const el = containerRef.value
  chart = createChart(el, {
    width: el.clientWidth,
    height: 320,
    layout: {
      background: { type: ColorType.Solid, color: '#ffffff' },
      textColor: '#475569',
      fontSize: 11,
    },
    grid: {
      vertLines: { color: '#f1f5f9' },
      horzLines: { color: '#f1f5f9' },
    },
    rightPriceScale: { borderColor: '#e5e7eb', minimumWidth: 60 },
    timeScale: { borderColor: '#e5e7eb', timeVisible: false },
    localization: { locale: 'ko-KR' },
  })

  candleSeries = chart.addSeries(CandlestickSeries, {
    upColor: UP,
    downColor: DOWN,
    borderUpColor: UP,
    borderDownColor: DOWN,
    wickUpColor: UP,
    wickDownColor: DOWN,
  })

  volumeSeries = chart.addSeries(HistogramSeries, {
    priceFormat: { type: 'volume' },
    priceScaleId: '', // 오버레이 스케일
  })
  volumeSeries.priceScale().applyOptions({
    scaleMargins: { top: 0.8, bottom: 0 }, // 하단 20%에 거래량 표시
  })

  applyData()

  // 가시 구간(logical index range) 변경 → 부모로 전달(캔들이 주(主))
  chart.timeScale().subscribeVisibleLogicalRangeChange((range) => {
    if (range) emit('visible-range-change', { from: range.from, to: range.to })
  })

  // 컨테이너 폭 변화를 수동 추적(계획서 4.6절 4 — lightweight-charts 필수)
  resizeObserver = new ResizeObserver((entries) => {
    const width = entries[0].contentRect.width
    if (width > 0) chart.applyOptions({ width })
  })
  resizeObserver.observe(el)
})

watch(
  () => props.items,
  () => applyData(),
)

onBeforeUnmount(() => {
  if (resizeObserver) resizeObserver.disconnect()
  if (chart) chart.remove()
  chart = null
})
</script>

<template>
  <div ref="containerRef" class="chart-box chart-candle"></div>
</template>
