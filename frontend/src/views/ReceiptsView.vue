<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Search } from '@element-plus/icons-vue'
import type { ReceiptRow, ReceiptStatus, ReceiptSummary } from '../api/types'
import { datePresets, debounce, dtShort, num } from '../format'
import { usePagedList } from '../composables/usePagedList'
import { useNarrow } from '../composables/useNarrow'
import PageHeader from '../components/PageHeader.vue'
import Pager from '../components/Pager.vue'
import EmptyState from '../components/EmptyState.vue'

// Danh sach phieu nhap. Bam 1 phieu -> /receipts/:id (xem / sua nhap / ghi so / huy).
const route = useRoute()
const router = useRouter()
const narrow = useNarrow()
const list = usePagedList<ReceiptRow, ReceiptSummary>('/receipts', { status: 'POSTED', from: '', to: '', q: '', supplierId: 0 },
  { params: (q) => ({ ...q, supplierId: q.supplierId || undefined }) })
const { query, items, total, summary, loading, failed } = list
const isDraft = computed(() => query.status === 'DRAFT')

const tabs: { value: ReceiptStatus; label: string }[] = [
  { value: 'POSTED', label: 'Đã ghi sổ' },
  { value: 'DRAFT', label: 'Phiếu nháp' },
  { value: 'CANCELLED', label: 'Đã hủy' },
]
const presets = datePresets()
const preset = computed(() => presets.find((p) => p.from === query.from && p.to === query.to)?.key ?? (query.from ? 'custom' : 'all'))
const range = computed<[string, string] | null>(() => (query.from && query.to ? [String(query.from), String(query.to)] : null))
function pickPreset(k: string) {
  const p = presets.find((x) => x.key === k)
  list.set(p ? { from: p.from, to: p.to } : { from: '', to: '' })
}

const search = ref(String(query.q))
const pushSearch = debounce(() => list.set({ q: search.value.trim() }), 300)
watch(() => query.q, (v) => { if (v !== search.value.trim()) search.value = String(v) })
const filtered = computed(() => !!(query.q || query.from || query.supplierId))
function clearFilters() { search.value = ''; list.set({ q: '', from: '', to: '', supplierId: 0 }) }

function open(r: ReceiptRow) { router.push(`/receipts/${r.id}`) }

onMounted(list.load)
</script>

<template>
  <PageHeader title="Nhập hàng" subtitle="Phiếu nhập ghi sổ thì cộng tồn kho, tính lại giá vốn và ghi nợ nhà cung cấp. Muốn sửa phiếu đã ghi sổ: hủy rồi lập phiếu mới.">
    <router-link to="/suppliers"><el-button>Nhà cung cấp</el-button></router-link>
    <router-link to="/receipts/new"><el-button type="primary" :icon="Plus">Lập phiếu nhập</el-button></router-link>
  </PageHeader>

  <div class="tabs" role="tablist" aria-label="Trạng thái phiếu nhập">
    <button v-for="t in tabs" :key="t.value" type="button" role="tab" class="tab" :class="{ on: query.status === t.value }"
            :aria-selected="query.status === t.value" @click="list.set({ status: t.value, sort: '' })">
      {{ t.label }}<span class="count num">{{ num(summary?.countByStatus?.[t.value] ?? 0) }}</span>
    </button>
  </div>

  <div class="toolbar">
    <el-radio-group :model-value="preset" aria-label="Khoảng ngày" @change="(k: any) => pickPreset(k)">
      <el-radio-button value="all">Tất cả</el-radio-button>
      <el-radio-button v-for="p in presets" :key="p.key" :value="p.key">{{ p.label }}</el-radio-button>
    </el-radio-group>
    <el-date-picker :model-value="range" type="daterange" value-format="YYYY-MM-DD" format="DD/MM/YYYY" range-separator="–"
                    start-placeholder="Từ ngày" end-placeholder="Đến ngày" aria-label="Chọn khoảng ngày"
                    @update:model-value="(v: any) => list.set(v ? { from: v[0], to: v[1] } : { from: '', to: '' })" />
    <el-input v-model="search" :prefix-icon="Search" placeholder="Mã phiếu, nhà cung cấp, ghi chú" clearable class="search"
              aria-label="Tìm phiếu nhập" @input="pushSearch" @clear="pushSearch" />
  </div>

  <div class="panel">
    <div v-if="summary && !isDraft" class="sumbar" aria-live="polite">
      <span><b class="num">{{ num(summary.count) }}</b> phiếu</span>
      <span>Tiền hàng nhập <b class="num">{{ num(summary.total) }}</b></span>
      <span>Đã trả <b class="num tone-money">{{ num(summary.paid) }}</b></span>
    </div>
    <el-table v-loading="loading" :data="items" :default-sort="list.defaultSort()" row-class-name="clickable"
              @sort-change="list.onSort" @row-click="open">
      <el-table-column prop="code" label="Mã phiếu" width="120" sortable="custom">
        <template #default="{ row }">
          <router-link :to="`/receipts/${row.id}`" class="link mono" @click.stop>{{ row.code || 'Nháp' }}</router-link>
        </template>
      </el-table-column>
      <el-table-column prop="date" :label="isDraft ? 'Lập lúc' : 'Ghi sổ lúc'" width="150" sortable="custom">
        <template #default="{ row }"><span class="num">{{ dtShort(isDraft ? row.createdAt : row.postedAt) }}</span></template>
      </el-table-column>
      <el-table-column prop="supplierName" label="Nhà cung cấp" min-width="190" sortable="custom">
        <template #default="{ row }">{{ row.supplierName || 'Không ghi NCC' }}</template>
      </el-table-column>
      <el-table-column v-if="!narrow" label="Số dòng" width="90" align="right">
        <template #default="{ row }"><span class="num">{{ row.lineCount }}</span></template>
      </el-table-column>
      <el-table-column prop="total" label="Tiền hàng" width="140" align="right" sortable="custom">
        <template #default="{ row }"><span class="num">{{ num(row.total) }}</span></template>
      </el-table-column>
      <el-table-column v-if="!narrow" label="Đã trả" width="130" align="right">
        <template #default="{ row }"><span class="num">{{ num(row.paidAmount) }}</span></template>
      </el-table-column>
      <el-table-column v-if="!narrow" label="Còn nợ NCC" width="130" align="right">
        <template #default="{ row }">
          <span v-if="Number(row.total) > Number(row.paidAmount)" class="num tone-debt">{{ num(Number(row.total) - Number(row.paidAmount)) }}</span>
          <span v-else class="muted">Đã trả đủ</span>
        </template>
      </el-table-column>
      <el-table-column v-if="!narrow" prop="createdBy" label="Người lập" width="130" show-overflow-tooltip />
      <template #empty>
        <EmptyState v-if="failed" error title="Không tải được phiếu nhập" hint="Bấm Thử lại. Nếu vẫn lỗi, tải lại trang (F5) hoặc báo quản trị.">
          <el-button @click="list.load">Thử lại</el-button>
        </EmptyState>
        <EmptyState v-else-if="filtered" title="Không có phiếu khớp bộ lọc"><el-button @click="clearFilters">Bỏ lọc</el-button></EmptyState>
        <EmptyState v-else-if="isDraft" title="Không có phiếu nháp" hint="Phiếu đang lập dở mà chưa ghi sổ sẽ hiện ở đây." />
        <EmptyState v-else title="Chưa có phiếu nhập nào" hint="Lập phiếu nhập để đưa hàng vào kho và có giá vốn.">
          <router-link to="/receipts/new"><el-button type="primary" :icon="Plus">Lập phiếu nhập</el-button></router-link>
        </EmptyState>
      </template>
    </el-table>
    <Pager :page="query.page" :size="query.size" :total="total" unit="phiếu" @change="list.set" />
  </div>
</template>

<style scoped>
.tabs { display: flex; gap: var(--space-sm); border-bottom: 1px solid var(--steel-200); margin-bottom: var(--space-lg); overflow-x: auto; }
.tab {
  display: inline-flex; align-items: center; gap: var(--space-md); min-height: 40px; padding: 0 var(--space-xl);
  font: inherit; font-size: 14px; color: var(--ink-muted); background: none; border: 0; border-bottom: 2px solid transparent;
  margin-bottom: -1px; cursor: pointer; white-space: nowrap; transition: color 0.15s, border-color 0.15s;
}
.tab:hover { color: var(--ink); }
.tab.on { color: var(--steel-800); border-bottom-color: var(--steel-800); font-weight: 600; }
.count { font-size: 12px; padding: 1px 8px; border-radius: 999px; background: var(--steel-100); color: var(--ink-muted); font-weight: 500; }
.tab.on .count { background: var(--steel-800); color: #fff; }
.search { max-width: 320px; flex: 1 1 220px; }
.sumbar { display: flex; flex-wrap: wrap; gap: var(--space-sm) var(--space-2xl); padding: var(--space-lg) var(--space-xl); border-bottom: 1px solid var(--steel-100); font-size: 14px; color: var(--ink-muted); }
.sumbar b { color: var(--ink); font-weight: 600; }
.sumbar b.tone-money { color: var(--money); }
:deep(.clickable) { cursor: pointer; }
.link { color: var(--steel-800); font-weight: 500; text-decoration: none; }
.link:hover { text-decoration: underline; }
</style>
