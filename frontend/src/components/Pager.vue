<script setup lang="ts">
import { computed } from 'vue'
import { PAGE_SIZES } from '../composables/usePagedList'
import { useNarrow } from '../composables/useNarrow'

// Man hep: it nut trang hon de thanh phan trang khong tran ngang
const narrow = useNarrow('(max-width: 560px)')

// Thanh phan trang dung chung: "1–20 / 951 hóa đơn", chon so dong, chuyen trang.
const props = defineProps<{ page: number; size: number; total: number; unit?: string; compact?: boolean }>()
const emit = defineEmits<{ change: [patch: { page?: number; size?: number }] }>()

const range = computed(() => {
  if (!props.total) return `0 ${props.unit ?? 'dòng'}`
  const a = (props.page - 1) * props.size + 1
  const b = Math.min(props.total, props.page * props.size)
  return `${a.toLocaleString('vi-VN')}–${b.toLocaleString('vi-VN')} / ${props.total.toLocaleString('vi-VN')} ${props.unit ?? 'dòng'}`
})
</script>

<template>
  <nav v-if="total > 0" class="pager" aria-label="Phân trang">
    <span class="range num" aria-live="polite">{{ range }}</span>
    <el-select v-if="!compact" :model-value="size" class="size" aria-label="Số dòng mỗi trang" @update:model-value="(v: number) => emit('change', { size: v })">
      <el-option v-for="s in PAGE_SIZES" :key="s" :value="s" :label="`${s} dòng/trang`" />
    </el-select>
    <el-pagination :current-page="page" :page-size="size" :total="total" :pager-count="compact || narrow ? 5 : 7"
                   layout="prev, pager, next" background hide-on-single-page
                   @current-change="(p: number) => emit('change', { page: p })" />
  </nav>
</template>

<style scoped>
.pager { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-md) var(--space-lg); padding: var(--space-lg) var(--space-xl); border-top: 1px solid var(--steel-100); }
.range { font-size: 13px; color: var(--ink-muted); margin-right: auto; }
.size { width: 140px; }
/* nut trang du lon de bam (>= 32px, vung bam 44px nho padding) */
.pager :deep(.el-pager li), .pager :deep(.btn-prev), .pager :deep(.btn-next) { min-width: 34px; height: 34px; line-height: 34px; }
@media (max-width: 560px) { .size { display: none; } }
</style>
