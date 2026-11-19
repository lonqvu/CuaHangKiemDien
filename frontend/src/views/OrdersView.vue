<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Printer, Search, ChatDotRound } from '@element-plus/icons-vue'
import http from '../api/http'
import type { CancelEffect, Order, OrderRow, OrderStatus, OrderSummary } from '../api/types'
import { datePresets, debounce, dt, dtShort, money, num, orderMessage, qty } from '../format'
import { usePagedList } from '../composables/usePagedList'
import { useNarrow } from '../composables/useNarrow'
import PageHeader from '../components/PageHeader.vue'
import Pager from '../components/Pager.vue'
import EmptyState from '../components/EmptyState.vue'
import ZaloSendDialog from '../components/ZaloSendDialog.vue'
import OrderReviseDialog from '../components/OrderReviseDialog.vue'
import { openPdf } from '../api/pdf'
import { useAuth } from '../stores/auth'

// Thay OrderHistory + OrderDetailDialog cu. Phan trang, loc, sap xep o server; bo loc nam tren URL.
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const narrow = useNarrow()

const list = usePagedList<OrderRow, OrderSummary>('/orders', { status: 'PAID', from: '', to: '', q: '' })
const { query, items, total, summary, loading, failed } = list
const isDraft = computed(() => query.status === 'DRAFT')

const tabs: { value: OrderStatus; label: string }[] = [
  { value: 'PAID', label: 'Đã chốt' },
  { value: 'CANCELLED', label: 'Đã hủy' },
  { value: 'DRAFT', label: 'Nháp bỏ dở' },
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

const filtered = computed(() => !!(query.q || query.from))
function clearFilters() { search.value = ''; list.set({ q: '', from: '', to: '' }) }

// ---------- chi tiet: /orders/:id ----------
const detail = ref<Order | null>(null)
const detailLoading = ref(false)
const detailId = computed(() => (route.params.id ? Number(route.params.id) : null))

// lich su sua hoa don (chi quan tri xem)
interface Revision { id: number; revisedAt: string; revisedBy: string | null; reason: string | null; debtDelta: number; refund: number; before: string; after: string }
const revisions = ref<Revision[]>([])
const openRev = ref<number | null>(null)
async function loadRevisions(id: number) {
  revisions.value = auth.isAdmin ? (await http.get<Revision[]>(`/orders/${id}/revisions`).catch(() => ({ data: [] }))).data : []
}

watch(detailId, async (id) => {
  detail.value = null
  revisions.value = []
  openRev.value = null
  if (!id) return
  detailLoading.value = true
  try { detail.value = (await http.get<Order>(`/orders/${id}`)).data; loadRevisions(id) }
  catch { closeDetail() }
  finally { detailLoading.value = false }
}, { immediate: true })

// sua hoa don da chot (quan tri)
const revising = ref(false)
function onRevised(o: Order) {
  detail.value = o
  loadRevisions(o.id)
  list.load()
}

function openDetail(r: { id: number }) { router.push({ path: `/orders/${r.id}`, query: route.query }) }
function closeDetail() {
  // Mo tu danh sach -> Quay lai (giu vi tri); mo tu link ngoai -> thay URL
  if (String(window.history.state?.back ?? '').startsWith('/orders')) router.back()
  else router.replace({ path: '/orders', query: route.query })
}

async function cancel(o: Order) {
  // Xem truoc: no khach giam bao nhieu, cua hang phai tra lai bao nhieu tien mat (khach da tra no cua hoa don nay)
  const e = (await http.get<CancelEffect>(`/orders/${o.id}/cancel-preview`)).data
  const lines = [`Hóa đơn ${o.code} sẽ chuyển sang "Đã hủy", hàng được nhập lại kho. Dữ liệu không bị xóa.`]
  if (o.customerId) {
    if (Number(e.debtReversal) > 0) lines.push(`Nợ của ${o.customerName}: ${money(e.debtBefore)} → ${money(e.debtAfter)}.`)
    else if (Number(e.debtReversal) < 0) lines.push(`Nợ của ${o.customerName} tăng lại: ${money(e.debtBefore)} → ${money(e.debtAfter)} (hóa đơn này đã trừ bớt nợ cũ).`)
    if (Number(e.refund) > 0) lines.push(`Khách đã trả nợ của hóa đơn này nên cửa hàng phải TRẢ LẠI KHÁCH ${money(e.refund)} tiền mặt.`)
  }
  await ElMessageBox.confirm(lines.join('\n\n'), 'Hủy hóa đơn', {
    confirmButtonText: 'Hủy hóa đơn', cancelButtonText: 'Không hủy', type: 'warning', confirmButtonClass: 'el-button--danger', customClass: 'pre-line',
  })
  const data = (await http.post<Order>(`/orders/${o.id}/cancel`)).data
  detail.value = data
  list.load()
  if (Number(data.refundAmount) > 0) {
    // bat buoc bam xac nhan: viec tra tien mat nam ngoai phan mem, de khong bi quen
    await ElMessageBox.alert(`Trả lại ${data.customerName} ${money(data.refundAmount)} tiền mặt.\n\nNợ của khách đã về 0. Số tiền này được ghi trên hóa đơn ${data.code} và trong sổ công nợ.`,
      `Đã hủy ${data.code}: cần trả lại khách`, { confirmButtonText: 'Đã trả tiền cho khách', type: 'warning', customClass: 'pre-line', showClose: false })
  } else {
    ElMessage.success(`Đã hủy hóa đơn ${o.code}`)
  }
}

async function deleteDraft(r: OrderRow) {
  await ElMessageBox.confirm(`Xóa hóa đơn nháp lập lúc ${dt(r.createdAt)} (${num(r.itemsTotal)} ₫)? Không ảnh hưởng công nợ.`,
    'Xóa hóa đơn nháp', { confirmButtonText: 'Xóa nháp', cancelButtonText: 'Không xóa', type: 'warning', confirmButtonClass: 'el-button--danger' })
  await http.delete(`/orders/${r.id}`)
  ElMessage.success('Đã xóa hóa đơn nháp')
  list.load()
}

// In lai hoa don bang mau Jasper cu (Bill.jrxml)
function print(o: Order) { openPdf(`/orders/${o.id}/invoice.pdf`) }
const zalo = ref(false)

onMounted(list.load)
</script>

<template>
  <PageHeader title="Hóa đơn" subtitle="Lọc theo ngày chốt, giống cách tính của màn Thống kê. Hủy hóa đơn sẽ hoàn công nợ, không xóa dữ liệu." />

  <div class="tabs" role="tablist" aria-label="Trạng thái hóa đơn">
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
    <el-input v-model="search" :prefix-icon="Search" placeholder="Mã HĐ, tên/SĐT khách, nhân viên, ghi chú" clearable class="search"
              aria-label="Tìm hóa đơn" @input="pushSearch" @clear="pushSearch" />
  </div>

  <div class="panel">
    <div v-if="summary && !isDraft" class="sumbar" aria-live="polite">
      <span><b class="num">{{ num(summary.count) }}</b> hóa đơn</span>
      <span>Tiền hàng <b class="num">{{ num(summary.itemsTotal) }}</b></span>
      <span>Đã thu <b class="num tone-money">{{ num(summary.collected) }}</b></span>
      <span>Còn nợ trên hóa đơn <b class="num tone-debt">{{ num(summary.debtAfter) }}</b></span>
    </div>

    <el-table v-loading="loading" :data="items" :default-sort="list.defaultSort()" row-class-name="clickable"
              @sort-change="list.onSort" @row-click="(r: OrderRow) => !isDraft && openDetail(r)">
      <template v-if="!isDraft">
        <el-table-column prop="code" label="Mã HĐ" width="122" sortable="custom">
          <template #default="{ row }">
            <router-link :to="{ path: `/orders/${row.id}`, query: route.query }" class="code mono" @click.stop>{{ row.code }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="date" label="Ngày chốt" min-width="144" sortable="custom">
          <template #default="{ row }"><span class="num">{{ dtShort(row.checkedOutAt) }}</span></template>
        </el-table-column>
        <el-table-column prop="customerName" label="Khách hàng" min-width="160" sortable="custom">
          <template #default="{ row }">
            <div class="cus">{{ row.customerName || 'Khách lẻ' }}<span v-if="!row.customerId" class="tag">lẻ</span></div>
            <div v-if="row.customerPhone" class="muted small num">{{ row.customerPhone }}</div>
          </template>
        </el-table-column>
        <el-table-column v-if="!narrow" prop="staffName" label="Nhân viên" width="136" show-overflow-tooltip />
        <el-table-column prop="itemsTotal" label="Tiền hàng" min-width="120" align="right" sortable="custom">
          <template #default="{ row }"><span class="num">{{ num(row.itemsTotal) }}</span></template>
        </el-table-column>
        <el-table-column v-if="!narrow" prop="totalPayable" label="Tổng thanh toán" min-width="140" align="right" sortable="custom">
          <template #default="{ row }"><span class="num">{{ num(row.totalPayable) }}</span></template>
        </el-table-column>
        <el-table-column v-if="!narrow" prop="paidAmount" label="Khách trả" min-width="120" align="right" sortable="custom">
          <template #default="{ row }"><span class="num">{{ num(row.paidAmount) }}</span></template>
        </el-table-column>
        <el-table-column prop="debtAfter" label="Còn nợ" min-width="120" align="right" sortable="custom">
          <template #default="{ row }">
            <span v-if="Number(row.debtAfter) > 0" class="num tone-debt">{{ num(row.debtAfter) }}</span>
            <span v-else class="muted">Đã trả đủ</span>
          </template>
        </el-table-column>
      </template>
      <template v-else>
        <el-table-column prop="date" label="Lập lúc" width="160" sortable="custom">
          <template #default="{ row }"><span class="num">{{ dtShort(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column prop="staffName" label="Nhân viên" min-width="160" />
        <el-table-column prop="itemsTotal" label="Tiền hàng đang nhập" min-width="170" align="right" sortable="custom">
          <template #default="{ row }"><span class="num">{{ num(row.itemsTotal) }}</span></template>
        </el-table-column>
        <el-table-column min-width="230" align="right">
          <template #default="{ row }">
            <router-link :to="{ path: '/sale', query: { draft: row.id } }"><el-button link type="primary">Mở bán tiếp</el-button></router-link>
            <el-button link type="danger" @click.stop="deleteDraft(row)">Xóa nháp</el-button>
          </template>
        </el-table-column>
      </template>
      <template #empty>
        <EmptyState v-if="failed" error title="Không tải được danh sách hóa đơn" hint="Bấm Thử lại. Nếu vẫn lỗi, tải lại trang (F5) hoặc báo quản trị.">
          <el-button @click="list.load">Thử lại</el-button>
        </EmptyState>
        <EmptyState v-else-if="filtered" title="Không có hóa đơn khớp bộ lọc" hint="Thử bỏ bớt điều kiện hoặc chọn khoảng ngày khác.">
          <el-button @click="clearFilters">Bỏ lọc</el-button>
        </EmptyState>
        <EmptyState v-else-if="isDraft" title="Không có hóa đơn nháp bỏ dở" hint="Hóa đơn đang nhập ở màn Bán hàng mà chưa chốt sẽ hiện ở đây." />
        <EmptyState v-else title="Chưa có hóa đơn nào">
          <router-link to="/sale"><el-button type="primary">Bán hàng</el-button></router-link>
        </EmptyState>
      </template>
    </el-table>

    <Pager :page="query.page" :size="query.size" :total="total" unit="hóa đơn" @change="list.set" />
  </div>

  <el-drawer :model-value="!!detailId" :title="detail?.code ? `Hóa đơn ${detail.code}` : 'Hóa đơn'" size="min(1000px, 100%)" @close="closeDetail">
    <div v-loading="detailLoading" class="detail">
      <template v-if="detail">
        <dl class="meta">
          <div><dt>Trạng thái</dt><dd>
            <el-tag :type="detail.status === 'PAID' ? 'success' : detail.status === 'CANCELLED' ? 'danger' : 'info'" effect="plain">
              {{ detail.status === 'PAID' ? 'Đã chốt' : detail.status === 'CANCELLED' ? 'Đã hủy' : 'Nháp' }}
            </el-tag></dd></div>
          <div><dt>Chốt lúc</dt><dd class="num">{{ dtShort(detail.checkedOutAt) || '—' }}</dd></div>
          <div><dt>Khách</dt><dd>
            <router-link v-if="detail.customerId" :to="`/customers/${detail.customerId}`" class="code">{{ detail.customerName }}</router-link>
            <template v-else>{{ detail.customerName || 'Khách lẻ' }} <span class="muted small">(khách lẻ)</span></template>
          </dd></div>
          <div><dt>Nhân viên</dt><dd>{{ detail.staffName }}</dd></div>
          <div v-if="detail.customerPhone"><dt>SĐT</dt><dd class="num">{{ detail.customerPhone }}</dd></div>
          <div v-if="detail.customerAddress"><dt>Địa chỉ</dt><dd>{{ detail.customerAddress }}</dd></div>
          <div v-if="detail.note" class="wide"><dt>Ghi chú</dt><dd>{{ detail.note }}</dd></div>
        </dl>
        <el-table :data="detail.items" size="small">
          <el-table-column label="Mặt hàng" min-width="170">
            <template #default="{ row }">{{ row.productName }}
              <!-- dien thoai: gop SL x don gia vao duoi ten, chi con 2 cot -> khong bi cat -->
              <div v-if="narrow" class="muted small num">{{ qty(row.totalQuantity) }} {{ row.unit }} × {{ num(row.unitPrice) }}</div>
            </template>
          </el-table-column>
          <el-table-column v-if="!narrow" label="Tổng SL" min-width="120" align="right">
            <template #default="{ row }"><span class="num">{{ qty(row.totalQuantity) }}</span> <span class="muted small">{{ row.unit }}</span></template>
          </el-table-column>
          <el-table-column v-if="!narrow" label="Đơn giá" min-width="120" align="right"><template #default="{ row }"><span class="num">{{ num(row.unitPrice) }}</span></template></el-table-column>
          <el-table-column label="Thành tiền" :min-width="narrow ? 104 : 120" align="right"><template #default="{ row }"><span class="num">{{ num(row.lineTotal) }}</span></template></el-table-column>
        </el-table>
        <dl class="sum">
          <div><dt>Tiền hàng</dt><dd class="num">{{ num(detail.itemsTotal) }}</dd></div>
          <div><dt>Phí vận chuyển</dt><dd class="num">{{ num(detail.shippingFee) }}</dd></div>
          <div><dt>Nợ cũ</dt><dd class="num">{{ num(detail.previousDebt) }}</dd></div>
          <div class="b"><dt>Tổng thanh toán</dt><dd class="num">{{ num(detail.totalPayable) }}</dd></div>
          <div><dt>Khách trả</dt><dd class="num tone-money">{{ num(detail.paidAmount) }}</dd></div>
          <div class="b"><dt>{{ Number(detail.debtAfter) > 0 ? 'Còn nợ' : 'Còn lại' }}</dt>
            <dd class="num" :class="{ 'tone-debt': Number(detail.debtAfter) > 0 }">{{ num(detail.debtAfter) }}</dd></div>
          <div v-if="Number(detail.refundAmount) > 0" class="b refund"><dt>Đã trả lại khách (hủy / sửa)</dt><dd class="num">{{ num(detail.refundAmount) }}</dd></div>
        </dl>
        <section v-if="revisions.length" class="revs" aria-label="Lịch sử sửa hóa đơn">
          <h3>Đã sửa {{ revisions.length }} lần</h3>
          <div v-for="r in revisions" :key="r.id" class="rev">
            <button type="button" class="rev-head" :aria-expanded="openRev === r.id" @click="openRev = openRev === r.id ? null : r.id">
              <span class="num">{{ dtShort(r.revisedAt) }}</span> · {{ r.revisedBy }}
              <span v-if="r.reason"> · {{ r.reason }}</span>
              <span v-if="Number(r.debtDelta) !== 0" class="num" :class="Number(r.debtDelta) > 0 ? 'tone-debt' : 'tone-money'">
                · nợ {{ Number(r.debtDelta) > 0 ? '+' : '' }}{{ num(r.debtDelta) }}</span>
              <span v-if="Number(r.refund) > 0" class="tone-debt"> · trả lại khách {{ num(r.refund) }}</span>
            </button>
            <div v-if="openRev === r.id" class="rev-body">
              <div><b>Trước</b><pre>{{ r.before }}</pre></div>
              <div><b>Sau</b><pre>{{ r.after }}</pre></div>
            </div>
          </div>
        </section>
      </template>
    </div>
    <template #footer>
      <div class="foot">
        <el-button v-if="detail?.status === 'PAID' && auth.isAdmin" class="f-cancel" type="danger" plain @click="cancel(detail!)">Hủy hóa đơn</el-button>
        <el-button v-if="detail?.status === 'PAID' && auth.isAdmin" class="f-edit" plain @click="revising = true">Sửa hóa đơn</el-button>
        <span class="grow" />
        <el-button class="f-close" @click="closeDetail">Đóng</el-button>
        <el-button v-if="detail?.status === 'PAID'" class="f-zalo" type="success" plain :icon="ChatDotRound" @click="zalo = true">Gửi Zalo</el-button>
        <el-button v-if="detail && detail.status !== 'DRAFT'" class="f-print" type="primary" :icon="Printer" @click="print(detail!)">In hóa đơn</el-button>
      </div>
    </template>
  </el-drawer>
  <OrderReviseDialog v-if="detail && detail.status === 'PAID'" v-model="revising" :order="detail" @saved="onRevised" />
  <ZaloSendDialog v-if="detail" v-model="zalo" :title="`Hóa đơn ${detail.code}`" :image-path="`/orders/${detail.id}/invoice.png`"
                  :file-name="`${detail.code}.png`" :text="orderMessage(detail)" :phone="detail.customerPhone" />
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
.search { max-width: 340px; flex: 1 1 240px; }
.sumbar { display: flex; flex-wrap: wrap; gap: var(--space-sm) var(--space-2xl); padding: var(--space-lg) var(--space-xl); border-bottom: 1px solid var(--steel-100); font-size: 14px; color: var(--ink-muted); }
.sumbar b { color: var(--ink); font-weight: 600; }
.sumbar b.tone-money { color: var(--money); }
.sumbar b.tone-debt { color: var(--debt); }
:deep(.clickable) { cursor: pointer; }
.code { color: var(--steel-800); font-weight: 500; text-decoration: none; }
.code:hover { text-decoration: underline; }
.cus { display: flex; align-items: center; gap: var(--space-sm); }
.tag { font-size: 11px; padding: 0 6px; border-radius: 4px; background: var(--steel-100); color: var(--ink-muted); }
.small { font-size: 12px; }
.detail { min-height: 200px; }
.meta { margin: 0 0 var(--space-xl); display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-md) var(--space-xl); }
.meta .wide { grid-column: 1 / -1; }
.meta dt { font-size: 13px; color: var(--ink-muted); }
.meta dd { margin: 0; }
.sum { margin: var(--space-lg) 0 0 auto; max-width: 340px; }
.sum div { display: flex; justify-content: space-between; padding: 4px 0; }
.sum dd { margin: 0; }
.sum .b { font-weight: 600; border-top: 1px solid var(--steel-200); }
.sum .refund { color: var(--danger); }
.foot { display: flex; gap: var(--space-md); align-items: center; }
.foot .grow { flex: 1; }
.foot .el-button + .el-button { margin-left: 0; }
@media (max-width: 560px) { .meta { grid-template-columns: 1fr; } }
/* dien thoai: nut chia 2 cot, khong tran ngang; nut chinh (In, Zalo) len tren */
@media (max-width: 640px) {
  .foot { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-sm); }
  .foot .grow { display: none; }
  .foot .el-button { width: 100%; margin: 0; }
  .foot .f-print { order: 1; }
  .foot .f-zalo { order: 2; }
  .foot .f-edit { order: 3; }
  .foot .f-cancel { order: 4; }
  .foot .f-close { order: 5; grid-column: 1 / -1; }
}
.revs { margin-top: var(--space-lg); display: flex; flex-direction: column; gap: var(--space-sm); }
.revs h3 { font-size: 14px; margin: 0; }
.rev { border: 1px solid var(--steel-200); border-radius: var(--radius-md); }
.rev-head { width: 100%; text-align: left; background: none; border: 0; padding: var(--space-sm) var(--space-md); font: inherit; font-size: 13px; cursor: pointer; color: var(--ink); line-height: 1.5; }
.rev-head:hover { background: var(--steel-50); }
.rev-body { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-md); padding: 0 var(--space-md) var(--space-md); font-size: 13px; }
.rev-body pre { margin: 4px 0 0; white-space: pre-wrap; font-family: var(--font-sans); font-size: 13px; background: var(--steel-50); border-radius: var(--radius-sm, 6px); padding: var(--space-sm); }
@media (max-width: 640px) { .rev-body { grid-template-columns: 1fr; } }
</style>
