<script setup lang="ts">
import { computed } from 'vue'
import { num } from '../format'

// Bieu do cot doanh thu. Truc tung tinh theo trieu dong, moc tron (1-2-5); nhieu cot thi an so tren dau cot (van co tooltip + aria).
const props = defineProps<{ data: { label: string; value: number }[]; caption: string; granularity: string }>()

const niceMax = computed(() => {
  const m = Math.max(1, ...props.data.map((d) => d.value))
  const p = 10 ** Math.floor(Math.log10(m))
  return ([1, 2, 5, 10].find((k) => k * p >= m) ?? 10) * p
})
const ticks = computed(() => [1, 0.5, 0].map((f) => ({ f, label: (niceMax.value * f / 1e6).toLocaleString('vi-VN', { maximumFractionDigits: 1 }) })))
const showValues = computed(() => props.data.length <= 16)
const million = (v: number) => (v / 1e6).toLocaleString('vi-VN', { maximumFractionDigits: 1 })
</script>

<template>
  <figure class="chart">
    <div v-if="!data.length" class="empty muted">Chưa có hóa đơn đã chốt trong khoảng này</div>
    <div v-else class="plot">
      <div class="axis" aria-hidden="true">
        <span v-for="t in ticks" :key="t.f" class="num" :style="{ bottom: `calc(${t.f * 100}% - 8px)` }">{{ t.label }}</span>
      </div>
      <div class="area">
        <span v-for="t in ticks" :key="t.f" class="grid" :style="{ bottom: `${t.f * 100}%` }" aria-hidden="true" />
        <div class="bars" role="list" :aria-label="caption">
          <div v-for="d in data" :key="d.label" class="bar" role="listitem" :title="`${d.label}: ${num(d.value)} ₫`" :aria-label="`${d.label}: ${num(d.value)} đồng`">
            <span v-if="showValues && d.value" class="v num">{{ million(d.value) }}</span>
            <span class="fill" :style="{ height: `${(d.value / niceMax) * 100}%` }" />
          </div>
        </div>
        <div class="labels" aria-hidden="true">
          <span v-for="(d, i) in data" :key="d.label" class="l" :class="{ skip: data.length > 16 && i % Math.ceil(data.length / 12) !== 0 }">{{ d.label }}</span>
        </div>
      </div>
    </div>
    <figcaption class="note muted">Mỗi cột là 1 {{ granularity }}. Đơn vị trục: triệu đồng. Rê chuột lên cột để xem số chính xác.</figcaption>
  </figure>
</template>

<style scoped>
.chart { margin: 0; }
.plot { display: grid; grid-template-columns: 44px minmax(0, 1fr); gap: var(--space-md); }
.axis { position: relative; height: 220px; }
.axis span { position: absolute; right: 0; font-size: 12px; color: var(--ink-muted); line-height: 16px; }
.area { position: relative; min-width: 0; }
.grid { position: absolute; left: 0; right: 0; height: 1px; background: var(--steel-100); }
.bars { position: relative; display: flex; align-items: flex-end; gap: 3px; height: 220px; }
.bar { flex: 1 1 0; min-width: 0; height: 100%; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; gap: 2px; }
.fill { width: 100%; max-width: 40px; background: var(--steel-800); border-radius: 3px 3px 0 0; min-height: 1px; transition: background-color 0.15s; }
.bar:hover .fill { background: #2C5282; }
.v { font-size: 12px; color: var(--ink-muted); white-space: nowrap; }
.labels { display: flex; gap: 3px; margin-top: var(--space-sm); }
.l { flex: 1 1 0; min-width: 0; text-align: center; font-size: 12px; color: var(--ink-muted); white-space: nowrap; overflow: visible; }
.l.skip { visibility: hidden; }
.empty { height: 220px; display: grid; place-items: center; }
.note { margin-top: var(--space-md); font-size: 12px; }
</style>
