<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Printer, Search } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Customer, LedgerRow, PaymentResult } from '../api/types'
import { debounce, dt, isoDate, money, num } from '../format'
import PageHeader from '../components/PageHeader.vue'
import NumberField from '../components/NumberField.vue'
import DebtAdjustDialog from '../components/DebtAdjustDialog.vue'
import { openPdf } from '../api/pdf'

// Thay Customers + ThanhToanNo cu
const route = useRoute()
const rows = ref<Customer[]>([])
const q = ref('')
const onlyDebt = ref(route.query.debt === '1')
const loading = ref(false)

const shown = computed(() => (onlyDebt.value ? rows.value.filter((c) => Number(c.debt) > 0) : rows.value))
const totalDebt = computed(() => rows.value.reduce((a, c) => a + Number(c.debt), 0))

async function load() {
  loading.value = true
  try { rows.value = (await http.get<Customer[]>('/customers', { params: { q: q.value } })).data }
  finally { loading.value = false }
}
const search = debounce(load)

// ----- them / sua -----
const formRef = ref<FormInstance>()
const dialog = reactive({ open: false, id: null as number | null })
const form = reactive({ name: '', phone: '', address: '', initialDebt: 0 })
const rules: FormRules = { name: [{ required: true, message: 'Nhập tên khách', trigger: 'blur' }] }

function openForm(c?: Customer) {
  dialog.id = c?.id ?? null
  Object.assign(form, { name: c?.name ?? '', phone: c?.phone ?? '', address: c?.address ?? '', initialDebt: 0 })
  dialog.open = true
}
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  const body = { name: form.name, phone: form.phone, address: form.address }
  if (dialog.id) await http.put(`/customers/${dialog.id}`, body)
  else await http.post('/customers', { ...body, initialDebt: form.initialDebt || 0 }) // app cu: nhap no khi them khach
  dialog.open = false
  ElMessage.success('Đã lưu khách hàng')
  load()
}
async function hide(c: Customer) {
  await ElMessageBox.confirm(`Ẩn "${c.name}" khỏi danh sách? Hóa đơn và sổ nợ cũ vẫn được giữ.`, 'Ẩn khách hàng',
    { confirmButtonText: 'Ẩn', cancelButtonText: 'Không', type: 'warning' })
  await http.delete(`/customers/${c.id}`)
  current.value = null
  load()
}

// ----- so no + thu no -----
const current = ref<Customer | null>(null)
const ledger = ref<LedgerRow[]>([])
const payForm = reactive({ amount: 0, note: '', method: 'CASH' as 'CASH' | 'TRANSFER' })
const paying = ref(false)
const adjustOpen = ref(false)
// thu xong: hien so phieu + nut in ngay (khong tu mo ban in)
const paid = ref<{ ledgerId: number; receiptNo: string; amount: number; debtAfter: number } | null>(null)

async function open(c: Customer) {
  current.value = c
  Object.assign(payForm, { amount: 0, note: '', method: 'CASH' })
  ledger.value = (await http.get<LedgerRow[]>(`/customers/${c.id}/debt-ledger`)).data
}

async function refresh(c: Customer) {
  await load()
  await open(c)
}

async function collect() {
  const c = current.value!
  if (!(payForm.amount > 0)) return ElMessage.warning('Nhập số tiền khách trả')
  if (payForm.amount > Number(c.debt)) return ElMessage.warning('Số tiền lớn hơn số nợ hiện tại')
  if (paying.value) return
  paying.value = true
  try {
    const amount = payForm.amount
    const { data } = await http.post<PaymentResult>(`/customers/${c.id}/debt-payments`, payForm)
    paid.value = { ledgerId: data.ledgerId, receiptNo: data.receiptNo, amount, debtAfter: Number(data.customer.debt) }
    await refresh(data.customer)
  } finally {
    paying.value = false
  }
}

const ledgerRowClass = ({ row }: { row: LedgerRow }) => (row.voided ? 'voided' : '')

// Phieu thu no: in ngay sau khi thu hoac in lai tu so no (so lieu co dinh, in lai ra y nguyen)
function printReceipt(ledgerId: number) { openPdf(`/customers/debt-payments/${ledgerId}/receipt.pdf`) }

// Huy phieu thu nhap nham: them dong dao nguoc, phieu in lai co dau "DA HUY"
async function voidReceipt(r: LedgerRow) {
  const { value } = await ElMessageBox.prompt(
    `Hủy phiếu thu ${r.receiptNo} (${money(r.decrease)})? Nợ của khách sẽ tăng lại ${money(r.decrease)}. Phiếu vẫn được giữ, in lại sẽ có dấu "ĐÃ HỦY".`,
    'Hủy phiếu thu', { inputPlaceholder: 'Lý do (bắt buộc), VD: thu nhầm số tiền', inputPattern: /\S/, inputErrorMessage: 'Nhập lý do hủy',
      confirmButtonText: 'Hủy phiếu thu', cancelButtonText: 'Không', type: 'warning' })
  const { data } = await http.post<Customer>(`/customers/debt-payments/${r.id}/void`, { reason: value.trim() })
  ElMessage.success(`Đã hủy ${r.receiptNo}. Nợ hiện tại ${money(data.debt)}`)
  await refresh(data)
}

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

onMounted(load)
</script>

<template>
  <PageHeader title="Khách hàng & công nợ" subtitle="Bấm vào một khách để xem sổ nợ và thu nợ.">
    <el-button type="primary" :icon="Plus" @click="openForm()">Thêm khách hàng</el-button>
  </PageHeader>

  <div class="toolbar">
    <el-input v-model="q" :prefix-icon="Search" placeholder="Tìm tên hoặc số điện thoại" clearable style="max-width: 320px"
              aria-label="Tìm khách hàng" @input="search" />
    <el-checkbox v-model="onlyDebt">Chỉ khách đang nợ</el-checkbox>
    <span class="grow" />
    <span class="muted">Tổng nợ: <b class="num tone-debt">{{ money(totalDebt) }}</b></span>
  </div>

  <div class="panel">
    <el-table v-loading="loading" :data="shown" empty-text="Không có khách hàng" row-class-name="clickable" @row-click="open">
      <el-table-column prop="name" label="Tên khách" min-width="180" />
      <el-table-column prop="phone" label="SĐT" width="140" />
      <el-table-column prop="address" label="Địa chỉ" min-width="200" show-overflow-tooltip />
      <el-table-column label="Đang nợ" width="150" align="right" sortable :sort-method="(a: Customer, b: Customer) => a.debt - b.debt">
        <template #default="{ row }">
          <span v-if="Number(row.debt) > 0" class="num tone-debt">{{ num(row.debt) }}</span>
          <span v-else class="muted">Không nợ</span>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-drawer :model-value="!!current" :title="current?.name" size="min(700px, 100%)" @close="current = null">
    <template v-if="current">
      <div class="info">
        <p class="muted">{{ current.phone || 'Chưa có SĐT' }}<span v-if="current.address"> · {{ current.address }}</span></p>
        <div class="actions">
          <el-button @click="openForm(current)">Sửa thông tin</el-button>
          <el-button @click="adjustOpen = true">Điều chỉnh nợ</el-button>
          <el-button @click="openStatement">In sao kê</el-button>
          <el-tooltip :disabled="!(Number(current.debt) > 0)" content="Khách còn nợ: thu hết hoặc điều chỉnh nợ về 0 (ghi lý do) rồi mới ẩn được" placement="bottom">
            <span><el-button type="danger" plain :disabled="Number(current.debt) > 0" @click="hide(current)">Ẩn khách</el-button></span>
          </el-tooltip>
        </div>
      </div>

      <section class="collect">
        <div>
          <div class="muted">Đang nợ</div>
          <div class="debt num" :class="{ 'tone-debt': Number(current.debt) > 0 }">{{ money(current.debt) }}</div>
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

      <h3 class="ledger-title">Sổ công nợ <span class="muted">· mới nhất trước · Còn nợ = dòng dưới + Tăng − Giảm</span></h3>
      <el-table :data="ledger" size="small" empty-text="Chưa có phát sinh" :row-class-name="ledgerRowClass">
        <el-table-column label="Thời gian" width="100"><template #default="{ row }">{{ dt(row.createdAt) }}</template></el-table-column>
        <el-table-column label="Nội dung" min-width="170">
          <template #default="{ row }">
            <span>{{ row.description }}</span>
            <div v-if="row.createdBy" class="by muted">{{ row.createdBy }}</div>
          </template>
        </el-table-column>
        <el-table-column label="Tăng" width="86" align="right"><template #default="{ row }"><span v-if="row.increase" class="num tone-debt">{{ num(row.increase) }}</span></template></el-table-column>
        <el-table-column label="Giảm" width="86" align="right"><template #default="{ row }"><span v-if="row.decrease" class="num tone-money">{{ num(row.decrease) }}</span></template></el-table-column>
        <el-table-column label="Còn nợ" width="92" align="right"><template #default="{ row }"><span class="num">{{ num(row.balanceAfter) }}</span></template></el-table-column>
        <el-table-column width="76" align="right">
          <template #default="{ row }">
            <template v-if="row.receiptNo">
              <el-button link type="primary" :aria-label="`In phiếu ${row.receiptNo}`" @click="printReceipt(row.id)">In</el-button>
              <el-button v-if="row.canVoid" link type="danger" :aria-label="`Hủy phiếu ${row.receiptNo}`" @click="voidReceipt(row)">Hủy</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </el-drawer>

  <!-- thu no xong: so phieu + in ngay -->
  <el-dialog :model-value="!!paid" title="Đã thu nợ" width="420px" @close="paid = null">
    <template v-if="paid">
      <p class="paid-line">Phiếu <b class="mono">{{ paid.receiptNo }}</b>: thu <b class="num tone-money">{{ money(paid.amount) }}</b></p>
      <p class="paid-line">Còn nợ: <b class="num" :class="{ 'tone-debt': paid.debtAfter > 0 }">{{ money(paid.debtAfter) }}</b></p>
    </template>
    <template #footer>
      <el-button @click="paid = null">Đóng</el-button>
      <el-button type="primary" :icon="Printer" autofocus @click="printReceipt(paid!.ledgerId); paid = null">In phiếu thu</el-button>
    </template>
  </el-dialog>

  <DebtAdjustDialog v-model="adjustOpen" :customer="current" @saved="refresh" />

  <el-dialog v-model="stmt.open" title="In sao kê công nợ" width="460px">
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

  <el-dialog v-model="dialog.open" :title="dialog.id ? 'Sửa khách hàng' : 'Thêm khách hàng'" width="440px">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
      <el-form-item label="Tên khách" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="Số điện thoại"><el-input v-model="form.phone" inputmode="tel" /></el-form-item>
      <el-form-item label="Địa chỉ"><el-input v-model="form.address" /></el-form-item>
      <el-form-item v-if="!dialog.id" label="Nợ ban đầu (₫)">
        <NumberField v-model="form.initialDebt" kind="integer" :step="100000" />
        <span v-if="form.initialDebt >= 1000" class="echo">= {{ money(form.initialDebt) }}</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog.open = false">Hủy</el-button>
      <el-button type="primary" @click="save">Lưu</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
:deep(.clickable) { cursor: pointer; }
.info .actions { display: flex; flex-wrap: wrap; gap: var(--space-sm); }
.info .actions .el-button + .el-button { margin-left: 0; }
.info { display: flex; justify-content: space-between; align-items: center; gap: var(--space-md); flex-wrap: wrap; margin-bottom: var(--space-lg); }
.info p { margin: 0; }
.collect { display: grid; grid-template-columns: 1fr 1.4fr; gap: var(--space-xl); padding: var(--space-lg); background: var(--steel-50); border-radius: var(--radius-lg); margin-bottom: var(--space-xl); }
.debt { font-size: 26px; font-weight: 600; }
.pay { display: flex; flex-direction: column; gap: var(--space-sm); }
.pay-row { display: flex; gap: var(--space-sm); }
.amt { flex: 1; }
.lbl { font-size: 13px; font-weight: 500; }
.ledger-title { font-size: 15px; margin-bottom: var(--space-md); }
.ledger-title .muted { font-size: 12px; font-weight: 400; }
.by { font-size: 11px; }
:deep(.voided td) { color: var(--ink-muted); text-decoration: line-through; }
:deep(.voided td:last-child) { text-decoration: none; }
.paid-line { margin: 0 0 var(--space-sm); font-size: 15px; }
.presets { display: flex; flex-wrap: wrap; gap: var(--space-xs); margin-bottom: var(--space-md); }
.presets .el-button + .el-button { margin-left: 0; }
@media (max-width: 560px) { .collect { grid-template-columns: 1fr; } }
</style>
