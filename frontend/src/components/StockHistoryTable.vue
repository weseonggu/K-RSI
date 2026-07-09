<script setup>
defineProps({
  rows: { type: Array, required: true }, // 표시할 StockHistoryItem 배열
})

const priceFormat = new Intl.NumberFormat('ko-KR')
const volumeFormat = new Intl.NumberFormat('ko-KR')

function rsiClass(rsi) {
  if (rsi == null) return ''
  if (rsi >= 70) return 'overbought'
  if (rsi <= 30) return 'oversold'
  return ''
}
</script>

<template>
  <div class="table-wrap">
    <table class="ranking history">
      <thead>
        <tr>
          <th>일자</th>
          <th class="num">시가</th>
          <th class="num">고가</th>
          <th class="num">저가</th>
          <th class="num">종가</th>
          <th class="num">거래량</th>
          <th class="num">등락률</th>
          <th class="num">RSI</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.date">
          <td class="cell-name">{{ row.date }}</td>
          <td class="num" data-label="시가">{{ priceFormat.format(row.openPrice) }}</td>
          <td class="num" data-label="고가">{{ priceFormat.format(row.highPrice) }}</td>
          <td class="num" data-label="저가">{{ priceFormat.format(row.lowPrice) }}</td>
          <td class="num" data-label="종가">{{ priceFormat.format(row.closePrice) }}</td>
          <td class="num" data-label="거래량">{{ volumeFormat.format(row.volume) }}</td>
          <td
            class="num"
            data-label="등락률"
            :class="row.flucRt > 0 ? 'up' : row.flucRt < 0 ? 'down' : ''"
          >
            <template v-if="row.flucRt != null">
              {{ row.flucRt > 0 ? '+' : '' }}{{ row.flucRt.toFixed(2) }}%
            </template>
            <template v-else>-</template>
          </td>
          <td class="num" data-label="RSI" :class="rsiClass(row.rsi)">
            {{ row.rsi != null ? row.rsi.toFixed(2) : '-' }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
