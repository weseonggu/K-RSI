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

function rsiWidth(rsi) {
  if (rsi == null) return '0%'
  return `${Math.min(100, Math.max(0, rsi))}%`
}
</script>

<template>
  <div class="table-wrap">
    <table class="ranking">
      <thead>
        <tr>
          <th class="num">순위</th>
          <th>종목코드</th>
          <th>종목명</th>
          <th class="num">종가</th>
          <th class="num">등락률</th>
          <th class="num">RSI</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.isuCd">
          <td class="num cell-rank">
            <span class="rank-badge">{{ row.rank }}</span>
          </td>
          <td class="cell-code">{{ row.isuCd }}</td>
          <td class="cell-name">{{ row.isuNm }}</td>
          <td class="num cell-close" data-label="종가">
            {{ row.closePrice != null ? priceFormat.format(row.closePrice) : '-' }}
          </td>
          <td
            class="num cell-fluc"
            data-label="등락률"
            :class="row.flucRt > 0 ? 'up' : row.flucRt < 0 ? 'down' : ''"
          >
            <template v-if="row.flucRt != null">
              {{ row.flucRt > 0 ? '+' : '' }}{{ row.flucRt.toFixed(2) }}%
            </template>
            <template v-else>-</template>
          </td>
          <td class="num cell-rsi" data-label="RSI" :class="rsiClass(row.rsi)">
            <span class="rsi-cell">
              <span class="rsi-meter">
                <span class="rsi-fill" :style="{ width: rsiWidth(row.rsi) }"></span>
              </span>
              <span class="rsi-value">{{ row.rsi != null ? row.rsi.toFixed(2) : '-' }}</span>
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
