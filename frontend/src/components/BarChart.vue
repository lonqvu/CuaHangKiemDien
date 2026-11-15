<script setup lang="ts">
import { computed } from 'vue'
import { num } from '../format'

const props = defineProps<{ data: { label: string; value: number }[]; caption: string }>()
const max = computed(() => Math.max(1, ...props.data.map((d) => d.value)))
</script>

<template>
  <figure class="chart">
    <figcaption class="sr-only">{{ caption }}</figcaption>
    <div v-if="!data.length" class="empty muted">Chưa có số liệu trong khoảng này</div>
    <div v-else class="bars" role="list">
      <div v-for="d in data" :key="d.label" class="bar" role="listitem" :aria-label="`${d.label}: ${num(d.value)} đồng`">
        <span class="v num">{{ (d.value / 1e6).toLocaleString('vi-VN', { maximumFractionDigits: 1 }) }}</span>
        <span class="fill" :style="{ height: `${(d.value / max) * 100}%` }" />
        <span class="l">{{ d.label }}</span>
      </div>
    </div>
  </figure>
</template>

<style scoped>
.chart { margin: 0; }
.bars { display: flex; align-items: flex-end; gap: 6px; height: 220px; overflow-x: auto; padding-top: 18px; }
.bar { flex: 1 0 28px; height: 100%; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; gap: 4px; min-width: 28px; }
.fill { width: 100%; max-width: 40px; background: var(--steel-800); border-radius: 3px 3px 0 0; min-height: 2px; }
.v { font-size: 11px; color: var(--ink-muted); }
.l { font-size: 12px; color: var(--ink-muted); white-space: nowrap; }
.empty { height: 220px; display: grid; place-items: center; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
</style>
