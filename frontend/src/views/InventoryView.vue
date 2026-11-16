<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Van, Checked } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Category, ProductUnit, StockMovement, StockRow, StockSummary } from '../api/types'
import { debounce, dtShort, money, movementLabel, movementLink, num, qty } from '../format'
import { usePagedList } from '../composables/usePagedList'
import { useNarrow } from '../composables/useNarrow'
import PageHeader from '../components/PageHeader.vue'
import Pager from '../components/Pager.vue'
import EmptyState from '../components/EmptyState.vue'

// Ton kho theo don vi goc. /inventory/:id mo the kho cua 1 san pham.
const route = useRoute()
const router = useRouter()
const narrow = useNarrow()

const list = usePagedList<StockRow, StockSummary>('/stock', { q: '', categoryId: 0, filter: 'all', sort: '' })
const { query, items, total, summary, loading, failed } = list
const categories = ref<Category[]>([])

const search = ref(String(query.q))
const pushSearch = debounce(() => list.set({ q: search.value.trim() }), 300)
watch(() => query.q, (v) => { if (v !== search.value.trim()) search.value = String(v) })
const filtered = computed(() => !!query.q || query.categoryId !== 0 || query.filter !== 'all')
function clearFilters() { search.value = ''; list.set({ q: '', categoryId: 0, filter: 'all' }) }

const filters = [
  { value: 'all', label: 'Tất cả' },
  { value: 'low', label: 'Sắp hết' },
  { value: 'out', label: 'Hết hàng' },
  { value: 'negative', label: 'Âm kho' },
]

function status(r: StockRow) {
  if (Number(r.onHand) < 0) return { text: 'Âm kho', cls: 'danger' }
  if (Number(r.onHand) === 0) return { text: 'Hết hàng', cls: 'danger' }
  if (Number(r.minStock) > 0 && Number(r.onHand) <= Number(r.minStock)) return { text: 'Sắp hết', cls: 'warn' }
  return null
}

// ---------- the kho: /inventory/:id ----------
const currentId = computed(() => (route.params.id ? Number(route.params.id) : null))
const current = ref<StockRow | null>(null)
const units = ref<ProductUnit[]>([])
const moves = usePagedList<StockMovement>(() => `/stock/${currentId.value}/movements`, { size: 20 }, { sync: false })

async function loadCurrent() {
  const { data } = await http.get<{ stock: StockRow; units: ProductUnit[] }>(`/stock/${currentId.value}`)
  current.value = data.stock
  units.value = data.units
  minForm.value = Number(data.stock.minStock)
}
watch(currentId, async (id) => {
  current.value = null
  if (!id) return
  try { await loadCurrent(); moves.set({ page: 1 }) } catch { closeDetail() }
}, { immediate: true })

function openDetail(r: StockRow) { router.push({ path: `/inventory/${r.productId}`, query: route.query }) }
function closeDetail() {
  if (String(window.history.state?.back ?? '').startsWith('/inventory')) router.back()
  else router.replace({ path: '/inventory', query: route.query })
}

// ---------- dieu chinh / xuat huy ----------
const adj = reactive({ open: false, dir: 'out' as 'out' | 'in', qty: 0, reason: '', saving: false })
const reasons = ['Hao hụt khi cắt', 'Hàng hư hỏng, móp méo', 'Trả lại nhà cung cấp', 'Dùng nội bộ', 'Đếm lại thấy thừa']
function openAdjust() { Object.assign(adj, { open: true, dir: 'out', qty: 0, reason: '', saving: false }) }
async function saveAdjust() {
  if (!(adj.qty > 0)) return ElMessage.warning('Nhập số lượng lớn hơn 0')
  if (!adj.reason.trim()) return ElMessage.warning('Chọn hoặc nhập lý do điều chỉnh')
  adj.saving = true
  try {
    const signed = adj.dir === 'out' ? -adj.qty : adj.qty
    current.value = (await http.post<StockRow>(`/stock/${currentId.value}/adjust`, { qty: signed, reason: adj.reason })).data
    ElMessage.success(`Đã ${adj.dir === 'out' ? 'xuất' : 'nhập'} ${qty(adj.qty)} ${current.value.unit ?? ''}. Tồn còn ${qty(current.value.onHand)}`)
    adj.open = false
    moves.set({ page: 1 })
    list.load()
  } finally {
    adj.saving = false
  }
}

// ---------- muc ton toi thieu ----------
const minForm = ref(0)
async function saveMin() {
  await http.put(`/stock/${currentId.value}/min-stock`, { minStock: minForm.value || 0 })
  ElMessage.success('Đã lưu mức tồn tối thiểu')
  await loadCurrent()
  list.load()
}

onMounted(async () => {
  list.load()
  categories.value = (await http.get<Category[]>('/categories')).data
})
</script>

<template>
  <PageHeader title="Tồn kho" subtitle="Số tồn tính theo đơn vị bán của sản phẩm. Giá vốn bình quân, cập nhật mỗi lần nhập hàng.">
    <router-link to="/stock-counts"><el-button :icon="Checked">Kiểm kê</el-button></router-link>
    <router-link to="/receipts/new"><el-button type="primary" :icon="Van">Nhập hàng</el-button></router-link>
  </PageHeader>

  <div v-if="summary" class="cards">
    <button type="button" class="card" :class="{ on: query.filter === 'all' }" @click="list.set({ filter: 'all' })">
      <span class="muted">Giá trị tồn kho</span><b class="num">{{ money(summary.totalValue) }}</b>
      <small class="muted">{{ num(summary.count) }} mặt hàng đang bán</small>
    </button>
    <button type="button" class="card" :class="{ on: query.filter === 'low' }" @click="list.set({ filter: 'low' })">
      <span class="muted">Sắp hết (dưới mức tối thiểu)</span><b class="num" :class="{ 'tone-debt': summary.lowCount > 0 }">{{ num(summary.lowCount) }}</b>
      <small class="muted">Cần đặt thêm hàng</small>
    </button>
    <button type="button" class="card" :class="{ on: query.filter === 'negative' }" @click="list.set({ filter: 'negative' })">
      <span class="muted">Âm kho</span><b class="num" :class="{ 'tone-danger': summary.negativeCount > 0 }">{{ num(summary.negativeCount) }}</b>
      <small class="muted">Đã bán nhiều hơn số nhập: kiểm kê lại</small>
    </button>
  </div>

  <div class="toolbar">
    <el-radio-group :model-value="query.filter" aria-label="Lọc theo tình trạng tồn" @change="(v: any) => list.set({ filter: v })">
      <el-radio-button v-for="f in filters" :key="f.value" :value="f.value">{{ f.label }}</el-radio-button>
    </el-radio-group>
    <el-input v-model="search" :prefix-icon="Search" placeholder="Tìm tên sản phẩm" clearable class="search" aria-label="Tìm sản phẩm"
              @input="pushSearch" @clear="pushSearch" />
    <el-select :model-value="Number(query.categoryId)" class="cat" aria-label="Lọc theo loại" @change="(v: number) => list.set({ categoryId: v })">
      <el-option label="Tất cả loại" :value="0" />
      <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
    </el-select>
  </div>

  <div class="panel">
    <el-table v-loading="loading" :data="items" :default-sort="list.defaultSort()" row-class-name="clickable"
              @sort-change="list.onSort" @row-click="openDetail">
      <el-table-column prop="name" label="Sản phẩm" :min-width="narrow ? 160 : 230" sortable="custom">
        <template #default="{ row }">
          <router-link :to="{ path: `/inventory/${row.productId}`, query: route.query }" class="link" @click.stop>{{ row.name }}</router-link>
          <div v-if="narrow || row.categoryName" class="muted small">{{ row.categoryName || 'Chưa phân loại' }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="onHand" label="Tồn" width="150" align="right" sortable="custom">
        <template #default="{ row }">
          <span class="num" :class="{ 'tone-danger': Number(row.onHand) < 0 }">{{ qty(row.onHand) }}</span> <span class="muted small">{{ row.unit }}</span>
          <div v-if="status(row)" class="badge" :class="status(row)!.cls">{{ status(row)!.text }}</div>
        </template>
      </el-table-column>
      <el-table-column v-if="!narrow" prop="minStock" label="Tối thiểu" width="110" align="right" sortable="custom">
        <template #default="{ row }"><span class="num muted">{{ Number(row.minStock) ? qty(row.minStock) : '—' }}</span></template>
      </el-table-column>
      <el-table-column v-if="!narrow" prop="avgCost" label="Giá vốn" width="130" align="right" sortable="custom">
        <template #default="{ row }">
          <span v-if="Number(row.avgCost)" class="num">{{ num(Math.round(Number(row.avgCost))) }}</span>
          <span v-else class="muted small">Chưa có</span>
        </template>
      </el-table-column>
      <el-table-column v-if="!narrow" label="Giá bán" width="120" align="right">
        <template #default="{ row }"><span class="num muted">{{ num(row.price) }}</span></template>
      </el-table-column>
      <el-table-column prop="value" label="Giá trị tồn" width="150" align="right" sortable="custom">
        <template #default="{ row }"><span class="num">{{ num(row.value) }}</span></template>
      </el-table-column>
      <template #empty>
        <EmptyState v-if="failed" error title="Không tải được tồn kho" hint="Bấm Thử lại. Nếu vẫn lỗi, tải lại trang (F5) hoặc báo quản trị.">
          <el-button @click="list.load">Thử lại</el-button>
        </EmptyState>
        <EmptyState v-else-if="filtered" title="Không có mặt hàng khớp bộ lọc">
          <el-button @click="clearFilters">Bỏ lọc</el-button>
        </EmptyState>
        <EmptyState v-else title="Chưa có sản phẩm" hint="Thêm sản phẩm ở màn Sản phẩm trước." />
      </template>
    </el-table>
    <Pager :page="query.page" :size="query.size" :total="total" unit="mặt hàng" @change="list.set" />
  </div>

  <el-drawer :model-value="!!currentId" :title="current?.name ?? 'Thẻ kho'" size="min(760px, 100%)" @close="closeDetail">
    <template v-if="current">
      <div class="head">
        <div class="big">
          <span class="muted">Tồn hiện tại</span>
          <b class="num" :class="{ 'tone-danger': Number(current.onHand) < 0 }">{{ qty(current.onHand) }} <small>{{ current.unit }}</small></b>
          <span class="muted small">Giá vốn {{ Number(current.avgCost) ? money(Math.round(Number(current.avgCost))) : 'chưa có' }} · giá trị {{ money(current.value) }}</span>
        </div>
        <div class="side">
          <el-button @click="openAdjust">Điều chỉnh / xuất hủy</el-button>
          <router-link :to="{ path: '/receipts/new', query: { product: current.productId } }"><el-button type="primary" :icon="Van">Nhập hàng</el-button></router-link>
        </div>
      </div>

      <form class="min" @submit.prevent="saveMin">
        <label for="min">Tồn tối thiểu</label>
        <el-input-number id="min" v-model="minForm" :min="0" :controls="false" class="num-input" />
        <span class="muted small">{{ current.unit }}. Tồn bằng hoặc thấp hơn mức này thì báo "Sắp hết". Để 0 nếu không cần báo.</span>
        <el-button native-type="submit" size="small">Lưu</el-button>
      </form>
      <p v-if="units.length" class="muted small">Quy đổi khi nhập: <span v-for="(u, i) in units" :key="u.unit">{{ i ? ', ' : '' }}1 {{ u.unit }} = {{ qty(u.ratio) }} {{ current.unit }}</span></p>

      <h3 class="title">Thẻ kho <span class="muted">({{ moves.total.value }} dòng)</span></h3>
      <el-table v-loading="moves.loading.value" :data="moves.items.value" size="small" empty-text="Chưa có nhập xuất nào">
        <el-table-column label="Thời gian" width="140"><template #default="{ row }"><span class="num">{{ dtShort(row.createdAt) }}</span></template></el-table-column>
        <el-table-column label="Nghiệp vụ" min-width="150">
          <template #default="{ row }">
            <div>{{ movementLabel[row.type] }}
              <router-link v-if="movementLink(row.refType, row.refId)" :to="movementLink(row.refType, row.refId)!" class="link mono small">{{ row.refCode }}</router-link>
            </div>
            <div v-if="row.note && row.type === 'ADJUST'" class="muted small">{{ row.note }}</div>
          </template>
        </el-table-column>
        <el-table-column label="Nhập / xuất" width="110" align="right">
          <template #default="{ row }"><span class="num" :class="Number(row.qty) > 0 ? 'tone-money' : 'tone-debt'">{{ Number(row.qty) > 0 ? '+' : '' }}{{ qty(row.qty) }}</span></template>
        </el-table-column>
        <el-table-column label="Giá vốn" width="100" align="right"><template #default="{ row }"><span class="num">{{ num(Math.round(Number(row.unitCost))) }}</span></template></el-table-column>
        <el-table-column label="Tồn sau" width="96" align="right"><template #default="{ row }"><span class="num">{{ qty(row.balanceAfter) }}</span></template></el-table-column>
      </el-table>
      <Pager compact :page="moves.query.page" :size="moves.query.size" :total="moves.total.value" unit="dòng" @change="moves.set" />
    </template>
  </el-drawer>

  <el-dialog v-model="adj.open" :title="`Điều chỉnh tồn: ${current?.name ?? ''}`" width="min(460px, 94vw)">
    <el-form label-position="top" @submit.prevent="saveAdjust">
      <el-form-item label="Loại điều chỉnh">
        <el-radio-group v-model="adj.dir">
          <el-radio-button value="out">Xuất (hao hụt, hư hỏng)</el-radio-button>
          <el-radio-button value="in">Nhập thêm</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="`Số lượng (${current?.unit ?? ''})`">
        <el-input-number v-model="adj.qty" :min="0" :controls="false" style="width: 100%" class="num-input" />
      </el-form-item>
      <el-form-item label="Lý do">
        <el-select v-model="adj.reason" filterable allow-create default-first-option placeholder="Chọn hoặc gõ lý do" style="width: 100%">
          <el-option v-for="r in reasons" :key="r" :value="r" :label="r" />
        </el-select>
      </el-form-item>
      <p class="muted small">Định giá theo giá vốn hiện tại. Nhập hàng mua về thì dùng Phiếu nhập để ghi đúng giá nhập và công nợ nhà cung cấp.</p>
      <button type="submit" hidden />
    </el-form>
    <template #footer>
      <el-button @click="adj.open = false">Hủy</el-button>
      <el-button :type="adj.dir === 'out' ? 'danger' : 'primary'" :loading="adj.saving" @click="saveAdjust">{{ adj.dir === 'out' ? 'Ghi xuất kho' : 'Ghi nhập kho' }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-lg); margin-bottom: var(--space-lg); }
.card {
  display: flex; flex-direction: column; gap: 2px; text-align: left; padding: var(--space-lg) var(--space-xl); font: inherit; cursor: pointer;
  background: var(--surface); border: 1px solid var(--steel-200); border-radius: var(--radius-lg); transition: border-color 0.15s, box-shadow 0.15s;
}
.card:hover { border-color: var(--steel-400); }
.card.on { border-color: var(--steel-800); box-shadow: inset 0 0 0 1px var(--steel-800); }
.card span { font-size: 13px; }
.card b { font-size: 22px; font-weight: 600; }
.card small { font-size: 12px; }
.search { max-width: 300px; flex: 1 1 200px; }
.cat { width: 200px; }
:deep(.clickable) { cursor: pointer; }
.link { color: var(--steel-800); font-weight: 500; text-decoration: none; }
.link:hover { text-decoration: underline; }
.small { font-size: 12px; }
.badge { display: inline-block; font-size: 11px; font-weight: 600; padding: 0 6px; border-radius: 4px; margin-top: 2px; }
.badge.warn { background: #FEF3C7; color: var(--debt); }
.badge.danger { background: #FEE2E2; color: var(--danger); }
.head { display: flex; justify-content: space-between; align-items: flex-end; gap: var(--space-lg); flex-wrap: wrap; padding: var(--space-lg); background: var(--steel-50); border-radius: var(--radius-lg); }
.big { display: flex; flex-direction: column; gap: 2px; }
.big b { font-size: 28px; font-weight: 600; }
.big b small { font-size: 15px; font-weight: 500; color: var(--ink-muted); }
.side { display: flex; gap: var(--space-sm); flex-wrap: wrap; }
.side .el-button + .el-button { margin-left: 0; }
.min { display: flex; align-items: center; gap: var(--space-md); flex-wrap: wrap; margin: var(--space-lg) 0 var(--space-sm); }
.min label { font-size: 13px; font-weight: 500; }
.min .num-input { width: 120px; }
.num-input :deep(input) { text-align: right; font-family: var(--font-mono); }
.title { font-size: 15px; margin: var(--space-xl) 0 var(--space-md); }
.title .muted { font-weight: 400; font-size: 13px; }
@media (max-width: 760px) { .cards { grid-template-columns: 1fr; } }
</style>
