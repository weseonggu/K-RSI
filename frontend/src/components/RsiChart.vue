<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { createChart, LineSeries, LineStyle, ColorType } from 'lightweight-charts'

const props = defineProps({
  items: { type: Array, required: true }, // 오름차순 StockHistoryItem 배열
  visibleRange: { type: Object, default: null }, // 부모(캔들)가 내려주는 logical range
})

const containerRef = ref(null)
let chart = null
let rsiSeries = null
let resizeObserver = null

// rsi=null 구간은 whitespace data(time 만)로 넣어 gap 으로 표시(계획서 4.5절)
function toRsiData(items) {
  return items.map((it) => (it.rsi == null ? { time: it.date } : { time: it.date, value: it.rsi }))
}

function applyData() {
  if (!rsiSeries) return
  rsiSeries.setData(toRsiData(props.items))
}

onMounted(() => {
  const el = containerRef.value
  chart = createChart(el, {
    width: el.clientWidth,
    height: 140,
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

  rsiSeries = chart.addSeries(LineSeries, {
    color: '#7c3aed',
    lineWidth: 2,
    priceLineVisible: false,
    lastValueVisible: true,
  })
  // RSI 는 0~100 고정 스케일이 자연스럽다
  rsiSeries.applyOptions({ autoscaleInfoProvider: () => ({ priceRange: { minValue: 0, maxValue: 100 } }) })

  // 30/70 기준선(과매도/과매수)
  rsiSeries.createPriceLine({ price: 70, color: '#dc2626', lineStyle: LineStyle.Dashed, lineWidth: 1, title: '70' })
  rsiSeries.createPriceLine({ price: 30, color: '#2563eb', lineStyle: LineStyle.Dashed, lineWidth: 1, title: '30' })

  applyData()
  applyVisibleRange()

  resizeObserver = new ResizeObserver((entries) => {
    const width = entries[0].contentRect.width
    if (width > 0) chart.applyOptions({ width })
  })
  resizeObserver.observe(el)
})

// 캔들 차트가 준 가시 구간을 그대로 적용(단방향 동기화 — 무한 루프 방지)
function applyVisibleRange() {
  if (!chart || !props.visibleRange) return
  chart.timeScale().setVisibleLogicalRange({
    from: props.visibleRange.from,
    to: props.visibleRange.to,
  })
}

watch(
  () => props.items,
  () => applyData(),
)
watch(
  () => props.visibleRange,
  () => applyVisibleRange(),
)

onBeforeUnmount(() => {
  if (resizeObserver) resizeObserver.disconnect()
  if (chart) chart.remove()
  chart = null
})
</script>

<template>
  <div ref="containerRef" class="chart-box chart-rsi"></div>
</template>
