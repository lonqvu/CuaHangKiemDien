<script setup lang="ts">
import NumberField from '../components/NumberField.vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Search } from '@element-plus/icons-vue'
import http from '../api/http'
import type { StockCount, StockCountLine } from '../api/types'
import { dtShort, num, qty, stockUnit } from '../format'

/*
 * Phieu kiem ke: nhap so dem tung mat hang (tu luu sau 1 giay), ghi so -> kho dieu chinh bang chenh lech.
 * Chenh lech tinh voi ton LUC GHI SO (trong luc dem van ban hang binh thuong).
 * Mat hang chua co gia von (chua tung nhap): nhap gia von de dinh gia phan ton dau ky.
 */
const route = useRoute()
const router = useRouter()
const count = ref<StockCount | null>(null)
const busy = ref(false)
const q = ref('')
const onlyDiff = ref(false)
const id = computed(() => Number(route.params.id))
const draft = computed(() => count.value?.status === 'DRAFT')

const diffOf = (l: StockCountLine) => (l.countedQty == null ? null : Number(l.countedQty) - Number(l.systemQty ?? 0))
const shown = computed(() => {
  const k = q.value.trim().toLowerCase()
  return (count.value?.lines ?? []).filter((l) => (!k || l.productName.toLowerCase().includes(k))
    && (!onlyDiff.value || (diffOf(l) ?? 0) !== 0 || (draft.value ? false : Number(l.diff ?? 0) !== 0)))
})
const counted = computed(() => (count.value?.lines ?? []).filter((l) => l.countedQty != null).length)
const changed = computed(() => (count.value?.lines ?? []).filter((l) => (draft.value ? diffOf(l) : Number(l.diff ?? 0)) && (draft.value ? diffOf(l) : Number(l.diff ?? 0)) !== 0).length)

async function load() { count.value = (await http.get<StockCount>(`/stock-counts/${id.value}`)).data }
watch(id, load)

// Luu tu dong (debounce 800ms) de dem hang lau khong mat so
let timer: ReturnType<typeof setTimeout> | undefined
const dirty = new Set<number>()
const saving = ref(false)
const savedAt = ref<Date | null>(null)
function touch(l: StockCountLine) {
  dirty.add(l.id)
  clearTimeout(timer)
  timer = setTimeout(flush, 800)
}
async function flush() {
  if (!dirty.size || !count.value) return
  const ids = [...dirty]
  dirty.clear()
  saving.value = true
  try {
    await http.put(`/stock-counts/${id.value}/lines`, count.value.lines.filter((l) => ids.includes(l.id))
      .map((l) => ({ lineId: l.id, countedQty: l.countedQty, unitCost: l.unitCost })))
    savedAt.value = new Date()
  } catch {
    ids.forEach((i) => dirty.add(i)) // giu lai de lan sau luu
  } finally {
    saving.value = false
  }
}
onBeforeUnmount(() => { clearTimeout(timer); flush() })

function fillSystem() {
  count.value!.lines.forEach((l) => { if (l.countedQty == null) { l.countedQty = Number(l.systemQty ?? 0); touch(l) } })
}

async function post() {
  await flush()
  if (!counted.value) return ElMessage.warning('Chưa nhập số đếm cho mặt hàng nào')
  await ElMessageBox.confirm(`Ghi sổ ${counted.value} mặt hàng đã đếm (${changed.value} mặt hàng có chênh lệch)? Mặt hàng chưa đếm được bỏ qua. Sau khi ghi sổ không sửa được.`,
    'Ghi sổ kiểm kê', { confirmButtonText: 'Ghi sổ', cancelButtonText: 'Xem lại', type: 'info' })
  busy.value = true
  try {
    count.value = (await http.post<StockCount>(`/stock-counts/${id.value}/post`)).data
    ElMessage.success(`Đã ghi sổ ${count.value.code}. Tồn kho đã điều chỉnh theo số đếm.`)
  } finally {
    busy.value = false
  }
}

async function remove() {
  await ElMessageBox.confirm('Xóa phiếu kiểm kê đang đếm? Số đã nhập sẽ mất, tồn kho không đổi.', 'Xóa phiếu',
    { confirmButtonText: 'Xóa phiếu', cancelButtonText: 'Không', type: 'warning', confirmButtonClass: 'el-button--danger' })
  clearTimeout(timer)
  dirty.clear()
  await http.delete(`/stock-counts/${id.value}`)
  router.push('/stock-counts?status=DRAFT')
}

onMounted(load)
</script>

<template>
  <div v-if="count" class="wrap">
    <header class="top">
      <div>
        <router-link to="/stock-counts" class="back">← Kiểm kê</router-link>
        <h1>{{ count.code ? `Phiếu kiểm kê ${count.code}` : 'Phiếu kiểm kê (đang đếm)' }}</h1>
        <p class="muted">{{ count.note }} · lập {{ dtShort(count.createdAt) }} bởi {{ count.createdBy }}<template v-if="count.postedAt"> · ghi sổ {{ dtShort(count.postedAt) }}</template></p>
      </div>
      <div class="state">
        <span class="num"><b>{{ counted }}</b> / {{ count.lines.length }} đã đếm</span>
        <span v-if="draft" class="muted small" aria-live="polite">{{ saving ? 'Đang lưu...' : savedAt ? `Đã lưu ${savedAt.toLocaleTimeString('vi-VN')}` : 'Số đếm tự lưu' }}</span>
      </div>
    </header>

    <div class="toolbar">
      <el-input v-model="q" :prefix-icon="Search" placeholder="Tìm sản phẩm trong phiếu" clearable class="search" aria-label="Tìm sản phẩm" />
      <el-checkbox v-model="onlyDiff" border>Chỉ dòng chênh lệch</el-checkbox>
      <span class="grow" />
      <el-button v-if="draft" @click="fillSystem">Điền số sổ sách cho dòng chưa đếm</el-button>
    </div>

    <div class="panel">
      <el-table :data="shown" row-key="id" :empty-text="q || onlyDiff ? 'Không có dòng khớp' : 'Phiếu trống'">
        <el-table-column prop="productName" label="Sản phẩm" min-width="220" />
        <el-table-column :label="draft ? 'Sổ sách (hiện tại)' : 'Sổ sách lúc ghi'" min-width="150" align="right">
          <template #default="{ row }"><span class="num">{{ row.systemQty == null ? '—' : qty(row.systemQty) }}</span> <span class="muted small">{{ stockUnit(row.unit) }}</span></template>
        </el-table-column>
        <el-table-column label="Đếm thực tế" min-width="150" align="right">
          <template #default="{ row }">
            <NumberField v-if="draft" v-model="row.countedQty" kind="decimal" nullable class="n" placeholder="Chưa đếm"
                         :aria-label="`Số đếm ${row.productName}`" @change="touch(row)" />
            <span v-else class="num">{{ row.countedQty == null ? 'Bỏ qua' : qty(row.countedQty) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Chênh lệch" min-width="120" align="right">
          <template #default="{ row }">
            <template v-if="(draft ? diffOf(row) : row.diff) != null">
              <span v-if="Number(draft ? diffOf(row) : row.diff) === 0" class="muted">Khớp</span>
              <span v-else class="num" :class="Number(draft ? diffOf(row) : row.diff) > 0 ? 'tone-money' : 'tone-debt'">
                {{ Number(draft ? diffOf(row) : row.diff) > 0 ? 'Thừa +' : 'Thiếu ' }}{{ qty(Math.abs(Number(draft ? diffOf(row) : row.diff))) }}
              </span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="Giá vốn phần thừa" min-width="170" align="right">
          <template #default="{ row }">
            <NumberField v-if="draft && (diffOf(row) ?? 0) > 0" v-model="row.unitCost" kind="integer" nullable class="n"
                         placeholder="Theo giá vốn hiện tại" :aria-label="`Giá vốn ${row.productName}`" @change="touch(row)" />
            <span v-else-if="row.unitCost != null" class="num">{{ num(row.unitCost) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="draft" class="foot panel panel-pad">
      <p class="muted small">Nhập giá vốn cho phần thừa khi đây là <b>tồn đầu kỳ</b> hoặc mặt hàng chưa từng nhập; để trống thì tính theo giá vốn hiện tại.</p>
      <div class="actions">
        <el-button type="danger" plain @click="remove">Xóa phiếu</el-button>
        <span class="grow" />
        <el-button type="primary" size="large" :icon="Check" :loading="busy" @click="post">Ghi sổ ({{ counted }} mặt hàng)</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wrap { display: flex; flex-direction: column; gap: var(--space-lg); }
.top { display: flex; justify-content: space-between; align-items: flex-end; gap: var(--space-lg); flex-wrap: wrap; }
.top h1 { font-size: 22px; margin-top: var(--space-sm); }
.top p { margin: 2px 0 0; font-size: 14px; }
.back { font-size: 13px; color: var(--steel-800); text-decoration: none; }
.back:hover { text-decoration: underline; }
.state { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.toolbar { margin-bottom: 0; }
.search { max-width: 300px; flex: 1 1 200px; }
.grow { flex: 1; }
.n { width: 100%; }
.n :deep(input) { text-align: right; font-family: var(--font-mono); }
.small { font-size: 12px; }
.foot { display: flex; flex-direction: column; gap: var(--space-md); position: sticky; bottom: 0; z-index: 5; box-shadow: 0 -4px 12px rgba(15, 23, 42, 0.06); }
.foot p { margin: 0; }
.actions { display: flex; align-items: center; gap: var(--space-md); }
</style>
