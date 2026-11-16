<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Customer, CustomerSummary, LedgerRow, OrderRow } from '../api/types'
import { debounce, dtShort, money, num } from '../format'
import { usePagedList } from '../composables/usePagedList'
import { useNarrow } from '../composables/useNarrow'
import PageHeader from '../components/PageHeader.vue'
import Pager from '../components/Pager.vue'
import EmptyState from '../components/EmptyState.vue'
import { openPdf } from '../api/pdf'

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
const typeLabel: Record<string, string> = { ORDER: 'Mua hàng', PAYMENT: 'Trả nợ', ADJUST: 'Hủy hóa đơn', MANUAL: 'Sửa nợ' }
const payForm = reactive({ amount: 0, note: '' })
const paying = ref(false)

const ledger = usePagedList<LedgerRow>(() => `/customers/${currentId.value}/debt-ledger`, { size: 20 }, { sync: false })
const orders = usePagedList<OrderRow>('/orders', { size: 20 }, {
  sync: false, params: (q) => ({ ...q, customerId: currentId.value, status: 'PAID,CANCELLED' }),
})

watch(currentId, async (id) => {
  current.value = null
  if (!id) return
  tab.value = 'ledger'
  Object.assign(payForm, { amount: 0, note: '' })
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
  paying.value = true
  try {
    const { data } = await http.post<Customer>(`/customers/${c.id}/debt-payments`, payForm)
    ElMessage.success(`Đã thu ${money(payForm.amount)}. Còn nợ ${money(data.debt)}`)
    Object.assign(payForm, { amount: 0, note: '' })
    await refreshCurrent(data)
  } finally {
    paying.value = false
  }
}

// O "No" cua man Customers cu: sua thang so no
async function setDebt() {
  const c = current.value!
  const { value } = await ElMessageBox.prompt(`Nợ hiện tại: ${money(c.debt)}. Nhập số nợ đúng (chỉ chữ số, không dấu chấm):`, 'Sửa số nợ', {
    inputValue: String(Number(c.debt)), inputPattern: /^\d+$/, inputErrorMessage: 'Chỉ nhập chữ số, ví dụ 1500000',
    confirmButtonText: 'Lưu số nợ', cancelButtonText: 'Hủy',
  })
  const { data } = await http.post<Customer>(`/customers/${c.id}/debt-adjustments`, { debt: Number(value), note: 'Sửa số nợ' })
  ElMessage.success(`Đã đặt nợ thành ${money(data.debt)}`)
  await refreshCurrent(data)
}

// Bien lai thanh toan cong no bang mau Jasper cu (ThanhToanNoReport.jrxml)
function printReceipt() { openPdf(`/customers/${current.value!.id}/payment-receipt.pdf`) }

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
      <el-table-column prop="debt" label="Đang nợ" :width="narrow ? 130 : 160" align="right" sortable="custom">
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

  <el-drawer :model-value="!!currentId" :title="current?.name ?? 'Khách hàng'" size="min(720px, 100%)" @close="closeDetail">
    <template v-if="current">
      <div class="info">
        <p class="muted"><span class="num">{{ current.phone || 'Chưa có SĐT' }}</span><span v-if="current.address"> · {{ current.address }}</span></p>
        <div class="actions">
          <el-button size="small" @click="openForm(current)">Sửa thông tin</el-button>
          <el-button size="small" @click="setDebt">Sửa số nợ</el-button>
          <el-button size="small" @click="printReceipt">In biên lai công nợ</el-button>
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
            <el-input-number id="amt" v-model="payForm.amount" :min="0" :max="Number(current.debt)" :step="100000" :controls="false" class="amt" />
            <el-button @click="payForm.amount = Number(current.debt)">Trả hết</el-button>
          </div>
          <el-input v-model="payForm.note" placeholder="Ghi chú: tiền mặt, chuyển khoản..." aria-label="Ghi chú thu nợ" />
          <el-button type="success" native-type="submit" :loading="paying" :disabled="!(payForm.amount > 0)">Ghi nhận thu {{ money(payForm.amount) }}</el-button>
        </form>
      </section>

      <el-tabs v-model="tab">
        <el-tab-pane name="ledger" :label="`Sổ công nợ (${ledger.total.value})`">
          <el-table v-loading="ledger.loading.value" :data="ledger.items.value" size="small" empty-text="Chưa có phát sinh công nợ">
            <el-table-column label="Thời gian" width="140"><template #default="{ row }"><span class="num">{{ dtShort(row.createdAt) }}</span></template></el-table-column>
            <el-table-column label="Loại" width="104"><template #default="{ row }">{{ typeLabel[row.type] }}</template></el-table-column>
            <el-table-column label="Phát sinh" width="116" align="right">
              <template #default="{ row }"><span class="num" :class="Number(row.amount) < 0 ? 'tone-money' : 'tone-debt'">{{ Number(row.amount) > 0 ? '+' : '' }}{{ num(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column label="Còn nợ" width="112" align="right"><template #default="{ row }"><span class="num">{{ num(row.balanceAfter) }}</span></template></el-table-column>
            <el-table-column label="Ghi chú" min-width="150">
              <template #default="{ row }">
                <router-link v-if="row.orderId" :to="`/orders/${row.orderId}`" class="link">{{ row.note || row.orderCode }}</router-link>
                <span v-else>{{ row.note }}</span>
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
            <el-table-column label="Ngày chốt" width="140"><template #default="{ row }"><span class="num">{{ dtShort(row.checkedOutAt) }}</span></template></el-table-column>
            <el-table-column label="Tiền hàng" width="112" align="right"><template #default="{ row }"><span class="num">{{ num(row.itemsTotal) }}</span></template></el-table-column>
            <el-table-column label="Khách trả" width="112" align="right"><template #default="{ row }"><span class="num">{{ num(row.paidAmount) }}</span></template></el-table-column>
            <el-table-column label="Trạng thái" min-width="100">
              <template #default="{ row }"><span :class="row.status === 'CANCELLED' ? 'tone-danger' : 'muted'">{{ row.status === 'CANCELLED' ? 'Đã hủy' : 'Đã chốt' }}</span></template>
            </el-table-column>
          </el-table>
          <Pager compact :page="orders.query.page" :size="orders.query.size" :total="orders.total.value" unit="hóa đơn" @change="orders.set" />
        </el-tab-pane>
      </el-tabs>

      <div class="danger-zone">
        <el-button type="danger" plain size="small" @click="hide(current)">Ẩn khách khỏi danh sách</el-button>
      </div>
    </template>
  </el-drawer>

  <el-dialog v-model="dialog.open" :title="dialog.id ? 'Sửa khách hàng' : 'Thêm khách hàng'" width="min(440px, 94vw)">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
      <el-form-item label="Tên khách" prop="name"><el-input v-model="form.name" autocomplete="off" /></el-form-item>
      <el-form-item label="Số điện thoại"><el-input v-model="form.phone" inputmode="tel" type="tel" /></el-form-item>
      <el-form-item label="Địa chỉ"><el-input v-model="form.address" /></el-form-item>
      <el-form-item v-if="!dialog.id" label="Nợ ban đầu (₫)">
        <el-input-number v-model="form.initialDebt" :min="0" :step="100000" :controls="false" style="width: 100%" />
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
.amt :deep(input) { text-align: right; font-family: var(--font-mono); }
.lbl { font-size: 13px; font-weight: 500; }
.danger-zone { margin-top: var(--space-2xl); padding-top: var(--space-lg); border-top: 1px solid var(--steel-100); }
@media (max-width: 560px) { .collect { grid-template-columns: 1fr; } }
</style>
