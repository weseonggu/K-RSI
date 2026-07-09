<script setup>
// 라우팅 셸: 실제 화면은 RankingPage/StockDetailPage 가 렌더링한다.
// 필수수정 3: 상세 → 뒤로가기 시 랭킹 화면의 date/market/order/rsiBand/size/page 상태가
// 초기화되는 회귀를 막기 위해 RankingPage 를 keep-alive 로 유지한다.
</script>

<template>
  <RouterView v-slot="{ Component }">
    <keep-alive include="RankingPage">
      <component :is="Component" />
    </keep-alive>
  </RouterView>
</template>
