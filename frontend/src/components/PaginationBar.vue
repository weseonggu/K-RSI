<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, required: true }, // 0-base
  totalPages: { type: Number, required: true },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['change'])

// 현재 페이지 주변 ±2 + 처음/끝 페이지, 사이는 말줄임표
const pages = computed(() => {
  const total = props.totalPages
  const current = props.page
  const delta = 2
  const from = Math.max(0, current - delta)
  const to = Math.min(total - 1, current + delta)
  const range = []
  if (from > 0) range.push(0)
  if (from > 1) range.push('ellipsis-l')
  for (let i = from; i <= to; i++) range.push(i)
  if (to < total - 2) range.push('ellipsis-r')
  if (to < total - 1) range.push(total - 1)
  return range
})

function go(p) {
  if (p < 0 || p >= props.totalPages || p === props.page) return
  emit('change', p)
}
</script>

<template>
  <nav v-if="totalPages > 1" class="pagination" aria-label="페이지 이동">
    <button
      type="button"
      class="page-btn"
      :disabled="disabled || page === 0"
      @click="go(page - 1)"
    >
      이전
    </button>
    <template v-for="p in pages" :key="p">
      <span v-if="typeof p === 'string'" class="page-ellipsis">…</span>
      <button
        v-else
        type="button"
        class="page-btn page-num"
        :class="{ active: p === page }"
        :disabled="disabled"
        :aria-current="p === page ? 'page' : undefined"
        @click="go(p)"
      >
        {{ p + 1 }}
      </button>
    </template>
    <button
      type="button"
      class="page-btn"
      :disabled="disabled || page >= totalPages - 1"
      @click="go(page + 1)"
    >
      다음
    </button>
  </nav>
</template>
