<script setup lang="ts">
import { computed } from 'vue'
import { num } from '../format'

const props = defineProps<{ label: string; value: number; previous?: number; previousLabel?: string; unit?: string; tone?: 'money' | 'debt' }>()

// % thay doi so voi ky truoc; kem chu "tang/giam" de khong chi dua vao mau
const delta = computed(() => {
  if (props.previous === undefined) return null
  if (!props.previous) return props.value ? { text: 'Mới phát sinh', dir: 'up' } : null
  const p = ((props.value - props.previous) / props.previous) * 100
  return { text: `${p >= 0 ? 'Tăng' : 'Giảm'} ${Math.abs(p).toFixed(1).replace('.', ',')}%`, dir: p >= 0 ? 'up' : 'down' }
})
</script>

<template>
  <div class="stat panel">
    <div class="label">{{ label }}</div>
    <div class="value num" :class="tone && `tone-${tone}`">{{ num(value) }}<small v-if="unit">{{ unit }}</small></div>
    <div v-if="previous !== undefined" class="foot">
      <span v-if="delta" :class="['delta', delta.dir]">{{ delta.text }}</span>
      <span class="muted">{{ previousLabel }}: <span class="num">{{ num(previous) }}</span></span>
    </div>
    <div v-else class="foot"><slot /></div>
  </div>
</template>

<style scoped>
.stat { padding: var(--space-lg) var(--space-xl); display: flex; flex-direction: column; gap: var(--space-sm); min-width: 0; }
.label { font-size: 14px; color: var(--ink-muted); }
.value { font-size: 24px; font-weight: 600; line-height: 1.2; overflow-wrap: anywhere; }
.value small { font-size: 14px; font-weight: 500; margin-left: 4px; color: var(--ink-muted); }
.foot { font-size: 13px; display: flex; flex-wrap: wrap; gap: var(--space-md); color: var(--ink-muted); }
.delta { font-weight: 600; }
.delta.up { color: var(--money); }
.delta.down { color: var(--danger); }
</style>
