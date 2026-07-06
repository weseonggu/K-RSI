<script setup>
defineProps({
  rows: { type: Array, required: true },
})

const priceFormat = new Intl.NumberFormat('ko-KR')

function rsiClass(rsi) {
  if (rsi == null) return ''
  if (rsi >= 70) return 'overbought'
  if (rsi <= 30) return 'oversold'
  return ''
}
</script>

<template>
  <table class="ranking">
    <thead>
      <tr>
        <th>순위</th>
        <th>종목코드</th>
        <th>종목명</th>
        <th>종가</th>
        <th>등락률</th>
        <th>RSI</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="row in rows" :key="row.isuCd">
        <td class="num">{{ row.rank }}</td>
        <td>{{ row.isuCd }}</td>
        <td class="name">{{ row.isuNm }}</td>
        <td class="num">{{ priceFormat.format(row.closePrice) }}</td>
        <td class="num" :class="row.flucRt > 0 ? 'up' : row.flucRt < 0 ? 'down' : ''">
          {{ row.flucRt > 0 ? '+' : '' }}{{ row.flucRt.toFixed(2) }}%
        </td>
        <td class="num" :class="rsiClass(row.rsi)">
          {{ row.rsi != null ? row.rsi.toFixed(2) : '-' }}
        </td>
      </tr>
    </tbody>
  </table>
</template>
