<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Printer, Search, ChatDotRound } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Customer, CustomerSummary, LedgerRow, OrderRow, PaymentResult } from '../api/types'
import { debounce, dtShort, isoDate, money, num } from '../format'
import { usePagedList } from '../composables/usePagedList'
import { useNarrow } from '../composables/useNarrow'
import PageHeader from '../components/PageHeader.vue'
import Pager from '../components/Pager.vue'
import EmptyState from '../components/EmptyState.vue'
import { openPdf } from '../api/pdf'
import ZaloSendDialog from '../components/ZaloSendDialog.vue'
import NumberField from '../components/NumberField.vue'
import DebtAdjustDialog from '../components/DebtAdjustDialog.vue'

// Thay Customers + ThanhToanNo cu. /customers/:id mo thang so no cua mot khach.
const route = useRoute()
const router = useRouter()
const narrow = useNarrow()

const list = usePagedList<Customer, CustomerSummary>('/customers', { q: '', debt: false, sort: '' })
const { query, items, total, summary, loading, failed } = list

const search = ref(String(query.q))
const pushSearch = debounce(() => list.set({ q: search.value.trim() }), 300)
watch(() => query.q, (v) => { if (v !== search.value.trim()) search.value = String(v) })
const filtered = computed(() => !!query.q || !!query.debt)
function clearFilters() { search.value = ''; list.set({ q: '', debt: false }) }

// ----- them / sua -----
const formRef = ref<FormInstance>()
const dialog = reactive({ open: false, id: null as number | null, saving: false })
const form = reactive({ name: '', phone: '', address: '', initialDebt: 0 })
const rules: FormRules = { name: [{ required: true, message: 'Nhập tên khách', trigger: 'blur' }] }

function openForm(c?: Customer) {
  dialog.id = c?.id ?? null
  Object.assign(form, { name: c?.name ?? '', phone: c?.phone ?? '', address: c?.address ?? '', initialDebt: 0 })
  dialog.open = true
  nextTick(() => formRef.value?.clearValidate())
}
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  dialog.saving = true
  try {
    const body = { name: form.name, phone: form.phone, address: form.address }
    if (dialog.id) {
      current.value = (await http.put<Customer>(`/customers/${dialog.id}`, body)).data
    } else {
      const { data } = await http.post<Customer>('/customers', { ...body, initialDebt: form.initialDebt || 0 }) // app cu: nhap no khi them khach
      router.push({ path: `/customers/${data.id}`, query: route.query })
    }
    dialog.open = false
    ElMessage.success('Đã lưu khách hàng')
    list.load()
  } finally {
    dialog.saving = false
  }
}
async function hide(c: Customer) {
  await ElMessageBox.confirm(`Ẩn "${c.name}" khỏi danh sách? Hóa đơn và sổ nợ cũ vẫn được giữ.`, 'Ẩn khách hàng',
    { confirmButtonText: 'Ẩn khách', cancelButtonText: 'Không ẩn', type: 'warning', confirmButtonClass: 'el-button--danger' })
  await http.delete(`/customers/${c.id}`)
  ElMessage.success(`Đã ẩn ${c.name}`)
  closeDetail()
  list.load()
}

// ----- chi tiet: so no + thu no + hoa don -----
const currentId = computed(() => (route.params.id ? Number(route.params.id) : null))
const current = ref<Customer | null>(null)
const tab = ref<'ledger' | 'orders'>('ledger')
const payForm = reactive({ amount: 0, note: '', method: 'CASH' as 'CASH' | 'TRANSFER' })
const adjustOpen = ref(false)
// thu xong: hien so phieu + nut in / gui Zalo ngay (khong tu mo ban in)
const paid = ref<{ ledgerId: number; receiptNo: string; amount: number; debtAfter: number } | null>(null)
const paying = ref(false)

const ledger = usePagedList<LedgerRow>(() => `/customers/${currentId.value}/debt-ledger`, { size: 20 }, { sync: false })
const orders = usePagedList<OrderRow>('/orders', { size: 20 }, {
  sync: false, params: (q) => ({ ...q, customerId: currentId.value, status: 'PAID,CANCELLED' }),
})

watch(currentId, async (id) => {
  current.value = null
  if (!id) return
  tab.value = 'ledger'
  Object.assign(payForm, { amount: 0, note: '', method: 'CASH' })
  try {
    current.value = (await http.get<Customer>(`/customers/${id}`)).data
    ledger.set({ page: 1 })
    orders.set({ page: 1 })
  } catch { closeDetail() }
}, { immediate: true })

function openDetail(c: Customer) { router.push({ path: `/customers/${c.id}`, query: route.query }) }
function closeDetail() {
  if (String(window.history.state?.back ?? '').startsWith('/customers')) router.back()
  else router.replace({ path: '/customers', query: route.query })
}

async function refreshCurrent(c: Customer) {
  current.value = c
  ledger.set({ page: 1 })
  list.load()
}

async function collect() {
  const c = current.value!
  if (!(payForm.amount > 0)) return ElMessage.warning('Nhập số tiền khách trả')
  if (payForm.amount > Number(c.debt)) return ElMessage.warning(`Số tiền lớn hơn số nợ hiện tại (${money(c.debt)})`)
  if (paying.value) return
  paying.value = true
  try {
    const amount = payForm.amount
    const { data } = await http.post<PaymentResult>(`/customers/${c.id}/debt-payments`, payForm)
    paid.value = { ledgerId: data.ledgerId, receiptNo: data.receiptNo, amount, debtAfter: Number(data.customer.debt) }
    Object.assign(payForm, { amount: 0, note: '', method: 'CASH' })
    await refreshCurrent(data.customer)
  } finally {
    paying.value = false
  }
}

// Phieu thu no: in ngay sau khi thu hoac in lai tu so no (so lieu co dinh, in lai ra y nguyen)
function printReceipt(ledgerId: number) { openPdf(`/customers/debt-payments/${ledgerId}/receipt.pdf`) }

// Huy phieu thu nhap nham: them dong dao nguoc, phieu in lai co dau "DA HUY"
async function voidReceipt(r: LedgerRow) {
  const { value } = await ElMessageBox.prompt(
    `Hủy phiếu thu ${r.receiptNo} (${money(r.decrease)})? Nợ của khách sẽ tăng lại ${money(r.decrease)}. Phiếu vẫn được giữ, in lại sẽ có dấu "ĐÃ HỦY".`,
    'Hủy phiếu thu', { inputPlaceholder: 'Lý do (bắt buộc), VD: thu nhầm số tiền', inputPattern: /\S/, inputErrorMessage: 'Nhập lý do hủy',
      confirmButtonText: 'Hủy phiếu thu', cancelButtonText: 'Không', type: 'warning', confirmButtonClass: 'el-button--danger' })
  const { data } = await http.post<Customer>(`/customers/debt-payments/${r.id}/void`, { reason: value.trim() })
  ElMessage.success(`Đã hủy ${r.receiptNo}. Nợ hiện tại ${money(data.debt)}`)
  await refreshCurrent(data)
}
const ledgerRowClass = ({ row }: { row: LedgerRow }) => (row.voided ? 'voided' : '')

// Sao ke cong no theo khoang ngay
const stmt = reactive({ open: false, range: null as [string, string] | null })
function openStatement() {
  const now = new Date()
  stmt.range = [isoDate(new Date(now.getFullYear(), now.getMonth(), 1)), isoDate(now)]
  stmt.open = true
}
function setRange(k: 'month' | 'lastMonth' | 'year' | 'all') {
  const n = new Date()
  stmt.range = k === 'all' ? null
    : k === 'month' ? [isoDate(new Date(n.getFullYear(), n.getMonth(), 1)), isoDate(n)]
    : k === 'lastMonth' ? [isoDate(new Date(n.getFullYear(), n.getMonth() - 1, 1)), isoDate(new Date(n.getFullYear(), n.getMonth(), 0))]
    : [isoDate(new Date(n.getFullYear(), 0, 1)), isoDate(n)]
}
function printStatement() {
  const q = stmt.range ? `?from=${stmt.range[0]}&to=${stmt.range[1]}` : ''
  openPdf(`/customers/${current.value!.id}/statement.pdf${q}`)
  stmt.open = false
}

// Gui Zalo: anh phieu thu gan nhat (chua huy); khach chua co phieu thu nao thi gui tin nhan chu bao so no
const zalo = ref(false)
const zaloReceipt = ref<{ ledgerId: number; receiptNo: string } | null>(null)
const lastReceipt = computed(() => ledger.items.value.find((r) => r.receiptNo && !r.voided) ?? null)
function openZalo(r?: { ledgerId: number; receiptNo: string } | null) {
  zaloReceipt.value = r ?? (lastReceipt.value ? { ledgerId: lastReceipt.value.id, receiptNo: lastReceipt.value.receiptNo! } : null)
  zalo.value = true
}

onMounted(list.load)
</script>

<template>
  <PageHeader title="Khách hàng & công nợ" subtitle="Bấm vào một khách để xem sổ nợ, hóa đơn và thu nợ.">
    <el-button type="primary" :icon="Plus" @click="openForm()">Thêm khách hàng</el-button>
  </PageHeader>

  <div class="toolbar">
    <el-input v-model="search" :prefix-icon="Search" placeholder="Tên, số điện thoại hoặc địa chỉ" clearable class="search"
              aria-label="Tìm khách hàng" @input="pushSearch" @clear="pushSearch" />
    <el-checkbox :model-value="!!query.debt" border @change="(v: any) => list.set({ debt: !!v })">Chỉ khách đang nợ</el-checkbox>
  </div>

  <div class="panel">
    <div v-if="summary" class="sumbar" aria-live="polite">
      <span><b class="num">{{ num(summary.count) }}</b> khách{{ filtered ? ' khớp bộ lọc' : '' }}</span>
      <span><b class="num">{{ num(summary.customersInDebt) }}</b> khách đang nợ, tổng <b class="num tone-debt">{{ money(summary.totalDebt) }}</b></span>
    </div>
    <el-table v-loading="loading" :data="items" :default-sort="list.defaultSort()" row-class-name="clickable"
              @sort-change="list.onSort" @row-click="openDetail">
      <el-table-column prop="name" label="Tên khách" :min-width="narrow ? 170 : 200" sortable="custom">
        <template #default="{ row }">
          <router-link :to="{ path: `/customers/${row.id}`, query: route.query }" class="link" @click.stop>{{ row.name }}</router-link>
          <div v-if="narrow && row.phone" class="muted small num">{{ row.phone }}</div>
        </template>
      </el-table-column>
      <el-table-column v-if="!narrow" prop="phone" label="SĐT" width="140" sortable="custom">
        <template #default="{ row }"><span class="num">{{ row.phone }}</span></template>
      </el-table-column>
      <el-table-column v-if="!narrow" prop="address" label="Địa chỉ" min-width="220" show-overflow-tooltip />
      <el-table-column prop="debt" label="Đang nợ" :min-width="narrow ? 130 : 160" align="right" sortable="custom">
        <template #default="{ row }">
          <span v-if="Number(row.debt) > 0" class="num tone-debt">{{ num(row.debt) }}</span>
          <span v-else class="muted">Không nợ</span>
        </template>
      </el-table-column>
      <template #empty>
        <EmptyState v-if="failed" error title="Không tải được danh sách khách" hint="Bấm Thử lại. Nếu vẫn lỗi, tải lại trang (F5) hoặc báo quản trị.">
          <el-button @click="list.load">Thử lại</el-button>
        </EmptyState>
        <EmptyState v-else-if="filtered" title="Không có khách khớp bộ lọc" :hint="query.debt ? 'Không có khách nào đang nợ trong kết quả tìm.' : 'Thử tên ngắn hơn hoặc vài số cuối của SĐT.'">
          <el-button @click="clearFilters">Bỏ lọc</el-button>
        </EmptyState>
        <EmptyState v-else title="Chưa có khách hàng">
          <el-button type="primary" :icon="Plus" @click="openForm()">Thêm khách hàng</el-button>
        </EmptyState>
      </template>
    </el-table>
    <Pager :page="query.page" :size="query.size" :total="total" unit="khách" @change="list.set" />
  </div>

  <el-drawer :model-value="!!currentId" :title="current?.name ?? 'Khách hàng'" size="min(1000px, 100%)" @close="closeDetail">
    <template v-if="current">
      <div class="info">
        <p class="muted"><span class="num">{{ current.phone || 'Chưa có SĐT' }}</span><span v-if="current.address"> · {{ current.address }}</span></p>
        <div class="actions">
          <el-button @click="openForm(current)">Sửa thông tin</el-button>
          <el-button @click="adjustOpen = true">Điều chỉnh nợ</el-button>
          <el-button @click="openStatement">In sao kê</el-button>
          <el-button type="success" plain :icon="ChatDotRound" @click="openZalo()">Gửi Zalo</el-button>
        </div>
      </div>

      <section class="collect">
        <div>
          <div class="muted">{{ Number(current.debt) > 0 ? 'Đang nợ' : 'Công nợ' }}</div>
          <div class="debt num" :class="{ 'tone-debt': Number(current.debt) > 0 }">{{ money(current.debt) }}</div>
          <div v-if="Number(current.debt) <= 0" class="muted small">Khách không còn nợ</div>
        </div>
        <form v-if="Number(current.debt) > 0" class="pay" @submit.prevent="collect">
          <label for="amt" class="lbl">Thu nợ</label>
          <div class="pay-row">
            <NumberField id="amt" v-model="payForm.amount" kind="integer" :max="Number(current.debt)" :step="100000" class="amt" />
            <el-button @click="payForm.amount = Number(current.debt)">Trả hết</el-button>
          </div>
          <span v-if="payForm.amount >= 1000" class="echo">= {{ money(payForm.amount) }}</span>
          <el-radio-group v-model="payForm.method" aria-label="Hình thức thu">
            <el-radio-button value="CASH">Tiền mặt</el-radio-button>
            <el-radio-button value="TRANSFER">Chuyển khoản</el-radio-button>
          </el-radio-group>
          <el-input v-model="payForm.note" placeholder="Ghi chú (in trên phiếu), VD: con trai trả hộ" aria-label="Ghi chú thu nợ" />
          <el-button type="success" native-type="submit" :loading="paying" :disabled="!(payForm.amount > 0)">
            {{ payForm.amount > 0 ? `Ghi nhận thu ${money(payForm.amount)}` : 'Nhập số tiền khách trả' }}
          </el-button>
        </form>
      </section>

      <el-tabs v-model="tab">
        <el-tab-pane name="ledger" :label="`Sổ công nợ (${ledger.total.value})`">
          <p class="ledger-hint muted">Mới nhất trước · Còn nợ = dòng dưới + Tăng − Giảm</p>
          <el-table v-loading="ledger.loading.value" :data="ledger.items.value" size="small" empty-text="Chưa có phát sinh công nợ" :row-class-name="ledgerRowClass">
            <el-table-column label="Thời gian" min-width="140"><template #default="{ row }"><span class="num">{{ dtShort(row.createdAt) }}</span></template></el-table-column>
            <el-table-column label="Nội dung" min-width="170">
              <template #default="{ row }">
                <router-link v-if="row.orderId" :to="`/orders/${row.orderId}`" class="link">{{ row.description }}</router-link>
                <span v-else>{{ row.description }}</span>
                <div v-if="row.createdBy" class="by muted">{{ row.createdBy }}</div>
              </template>
            </el-table-column>
            <el-table-column label="Tăng" min-width="120" align="right"><template #default="{ row }"><span v-if="row.increase" class="num tone-debt">{{ num(row.increase) }}</span></template></el-table-column>
            <el-table-column label="Giảm" min-width="120" align="right"><template #default="{ row }"><span v-if="row.decrease" class="num tone-money">{{ num(row.decrease) }}</span></template></el-table-column>
            <el-table-column label="Còn nợ" min-width="120" align="right"><template #default="{ row }"><span class="num">{{ num(row.balanceAfter) }}</span></template></el-table-column>
            <el-table-column min-width="120" align="right">
              <template #default="{ row }">
                <template v-if="row.receiptNo">
                  <el-button link type="primary" :aria-label="`In phiếu ${row.receiptNo}`" @click="printReceipt(row.id)">In</el-button>
                  <el-button v-if="row.canVoid" link type="danger" :aria-label="`Hủy phiếu ${row.receiptNo}`" @click="voidReceipt(row)">Hủy</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <Pager compact :page="ledger.query.page" :size="ledger.query.size" :total="ledger.total.value" unit="dòng" @change="ledger.set" />
        </el-tab-pane>
        <el-tab-pane name="orders" :label="`Hóa đơn (${orders.total.value})`">
          <el-table v-loading="orders.loading.value" :data="orders.items.value" size="small" empty-text="Khách chưa có hóa đơn">
            <el-table-column label="Mã HĐ" width="120">
              <template #default="{ row }"><router-link :to="`/orders/${row.id}`" class="link mono">{{ row.code }}</router-link></template>
            </el-table-column>
            <el-table-column label="Ngày chốt" min-width="140"><template #default="{ row }"><span class="num">{{ dtShort(row.checkedOutAt) }}</span></template></el-table-column>
            <el-table-column label="Tiền hàng" min-width="120" align="right"><template #default="{ row }"><span class="num">{{ num(row.itemsTotal) }}</span></template></el-table-column>
            <el-table-column label="Khách trả" min-width="120" align="right"><template #default="{ row }"><span class="num">{{ num(row.paidAmount) }}</span></template></el-table-column>
            <el-table-column label="Trạng thái" min-width="100">
              <template #default="{ row }"><span :class="row.status === 'CANCELLED' ? 'tone-danger' : 'muted'">{{ row.status === 'CANCELLED' ? 'Đã hủy' : 'Đã chốt' }}</span></template>
            </el-table-column>
          </el-table>
          <Pager compact :page="orders.query.page" :size="orders.query.size" :total="orders.total.value" unit="hóa đơn" @change="orders.set" />
        </el-tab-pane>
      </el-tabs>

      <div class="danger-zone">
        <el-tooltip :disabled="!(Number(current.debt) > 0)" content="Khách còn nợ: thu hết hoặc điều chỉnh nợ về 0 (ghi lý do) rồi mới ẩn được" placement="top">
          <span><el-button type="danger" plain :disabled="Number(current.debt) > 0" @click="hide(current)">Ẩn khách khỏi danh sách</el-button></span>
        </el-tooltip>
      </div>
    </template>
  </el-drawer>

  <ZaloSendDialog v-if="current" v-model="zalo" :title="zaloReceipt ? `Phiếu thu ${zaloReceipt.receiptNo}` : `Công nợ ${current.name}`"
                  :pdf-path="zaloReceipt ? `/customers/debt-payments/${zaloReceipt.ledgerId}/receipt.pdf` : `/customers/${current.id}/statement.pdf`"
                  :file-name="zaloReceipt ? `PhieuThu_${zaloReceipt.receiptNo}.pdf` : `CongNo_${current.name}.pdf`" />

  <!-- thu no xong: so phieu + in / gui Zalo ngay -->
  <el-dialog :model-value="!!paid" title="Đã thu nợ" width="min(440px, 94vw)" @close="paid = null">
    <template v-if="paid">
      <p class="paid-line">Phiếu <b class="mono">{{ paid.receiptNo }}</b>: thu <b class="num tone-money">{{ money(paid.amount) }}</b></p>
      <p class="paid-line">Còn nợ: <b class="num" :class="{ 'tone-debt': paid.debtAfter > 0 }">{{ money(paid.debtAfter) }}</b></p>
    </template>
    <template #footer>
      <el-button @click="paid = null">Đóng</el-button>
      <el-button type="success" plain :icon="ChatDotRound" @click="openZalo({ ledgerId: paid!.ledgerId, receiptNo: paid!.receiptNo }); paid = null">Gửi Zalo</el-button>
      <el-button type="primary" :icon="Printer" autofocus @click="printReceipt(paid!.ledgerId); paid = null">In phiếu thu</el-button>
    </template>
  </el-dialog>

  <DebtAdjustDialog v-model="adjustOpen" :customer="current" @saved="refreshCurrent" />

  <el-dialog v-model="stmt.open" title="In sao kê công nợ" width="min(460px, 94vw)">
    <p class="muted" style="margin-top: 0">Số dư đầu kỳ, từng lần mua / trả / điều chỉnh trong khoảng ngày, số dư cuối kỳ.</p>
    <div class="presets">
      <el-button size="small" @click="setRange('month')">Tháng này</el-button>
      <el-button size="small" @click="setRange('lastMonth')">Tháng trước</el-button>
      <el-button size="small" @click="setRange('year')">Năm nay</el-button>
      <el-button size="small" @click="setRange('all')">Từ đầu</el-button>
    </div>
    <el-date-picker v-model="stmt.range" type="daterange" value-format="YYYY-MM-DD" format="DD/MM/YYYY" range-separator="–"
                    start-placeholder="Từ đầu" end-placeholder="Đến nay" style="width: 100%" />
    <template #footer>
      <el-button @click="stmt.open = false">Hủy</el-button>
      <el-button type="primary" :icon="Printer" @click="printStatement">In sao kê</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="dialog.open" :title="dialog.id ? 'Sửa khách hàng' : 'Thêm khách hàng'" width="min(440px, 94vw)">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
      <el-form-item label="Tên khách" prop="name"><el-input v-model="form.name" autocomplete="off" /></el-form-item>
      <el-form-item label="Số điện thoại"><el-input v-model="form.phone" inputmode="tel" type="tel" /></el-form-item>
      <el-form-item label="Địa chỉ"><el-input v-model="form.address" /></el-form-item>
      <el-form-item v-if="!dialog.id" label="Nợ ban đầu (₫)">
        <NumberField v-model="form.initialDebt" kind="integer" :step="100000" />
        <span v-if="form.initialDebt >= 1000" class="echo">= {{ money(form.initialDebt) }}</span>
        <div class="muted small">Nợ từ trước khi dùng phần mềm. Để 0 nếu khách không nợ.</div>
      </el-form-item>
      <button type="submit" hidden />
    </el-form>
    <template #footer>
      <el-button @click="dialog.open = false">Hủy</el-button>
      <el-button type="primary" :loading="dialog.saving" @click="save">Lưu khách hàng</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.search { max-width: 340px; flex: 1 1 240px; }
.sumbar { display: flex; flex-wrap: wrap; gap: var(--space-sm) var(--space-2xl); padding: var(--space-lg) var(--space-xl); border-bottom: 1px solid var(--steel-100); font-size: 14px; color: var(--ink-muted); }
.sumbar b { color: var(--ink); font-weight: 600; }
.sumbar b.tone-debt { color: var(--debt); }
:deep(.clickable) { cursor: pointer; }
.link { color: var(--steel-800); font-weight: 500; text-decoration: none; }
.link:hover { text-decoration: underline; }
.small { font-size: 12px; }
.info { display: flex; justify-content: space-between; align-items: center; gap: var(--space-md); flex-wrap: wrap; margin-bottom: var(--space-lg); }
.info p { margin: 0; }
.actions { display: flex; flex-wrap: wrap; gap: var(--space-sm); }
.actions .el-button + .el-button { margin-left: 0; }
.collect { display: grid; grid-template-columns: 1fr 1.4fr; gap: var(--space-xl); padding: var(--space-lg); background: var(--steel-50); border-radius: var(--radius-lg); margin-bottom: var(--space-md); }
.debt { font-size: 26px; font-weight: 600; }
.pay { display: flex; flex-direction: column; gap: var(--space-sm); }
.pay-row { display: flex; gap: var(--space-sm); }
.amt { flex: 1; }
.lbl { font-size: 13px; font-weight: 500; }
.ledger-hint { margin: 0 0 var(--space-sm); font-size: 12px; }
.by { font-size: 11px; }
:deep(.voided td) { color: var(--ink-muted); text-decoration: line-through; }
:deep(.voided td:last-child) { text-decoration: none; }
.paid-line { margin: 0 0 var(--space-sm); font-size: 15px; }
.presets { display: flex; flex-wrap: wrap; gap: var(--space-xs); margin-bottom: var(--space-md); }
.presets .el-button + .el-button { margin-left: 0; }
.danger-zone { margin-top: var(--space-2xl); padding-top: var(--space-lg); border-top: 1px solid var(--steel-100); }
@media (max-width: 560px) { .collect { grid-template-columns: 1fr; } }
</style>
