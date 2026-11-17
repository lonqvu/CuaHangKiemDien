<script setup lang="ts">
import NumberField from '../components/NumberField.vue'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search, Van } from '@element-plus/icons-vue'
import http from '../api/http'
import type { ReceiptRow, Supplier, SupplierLedgerRow, SupplierSummary } from '../api/types'
import { debounce, dtShort, money, num } from '../format'
import { usePagedList } from '../composables/usePagedList'
import { useNarrow } from '../composables/useNarrow'
import PageHeader from '../components/PageHeader.vue'
import Pager from '../components/Pager.vue'
import EmptyState from '../components/EmptyState.vue'

// Nha cung cap + cong no NCC (cung mau man Khach hang). /suppliers/:id mo so no 1 NCC.
const route = useRoute()
const router = useRouter()
const narrow = useNarrow()

const list = usePagedList<Supplier, SupplierSummary>('/suppliers', { q: '', debt: false, sort: '' })
const { query, items, total, summary, loading, failed } = list
const search = ref(String(query.q))
const pushSearch = debounce(() => list.set({ q: search.value.trim() }), 300)
watch(() => query.q, (v) => { if (v !== search.value.trim()) search.value = String(v) })
const filtered = computed(() => !!query.q || !!query.debt)

// ----- them / sua -----
const formRef = ref<FormInstance>()
const dialog = reactive({ open: false, id: null as number | null, saving: false })
const form = reactive({ name: '', phone: '', address: '', initialDebt: 0 })
const rules: FormRules = { name: [{ required: true, message: 'Nhập tên nhà cung cấp', trigger: 'blur' }] }
function openForm(s?: Supplier) {
  dialog.id = s?.id ?? null
  Object.assign(form, { name: s?.name ?? '', phone: s?.phone ?? '', address: s?.address ?? '', initialDebt: 0 })
  dialog.open = true
  nextTick(() => formRef.value?.clearValidate())
}
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  dialog.saving = true
  try {
    const body = { name: form.name, phone: form.phone, address: form.address }
    if (dialog.id) current.value = (await http.put<Supplier>(`/suppliers/${dialog.id}`, body)).data
    else {
      const { data } = await http.post<Supplier>('/suppliers', { ...body, initialDebt: form.initialDebt || 0 })
      router.push({ path: `/suppliers/${data.id}`, query: route.query })
    }
    dialog.open = false
    ElMessage.success('Đã lưu nhà cung cấp')
    list.load()
  } finally {
    dialog.saving = false
  }
}

// ----- chi tiet -----
const currentId = computed(() => (route.params.id ? Number(route.params.id) : null))
const current = ref<Supplier | null>(null)
const tab = ref<'ledger' | 'receipts'>('ledger')
const typeLabel: Record<string, string> = { RECEIPT: 'Nhập hàng', PAYMENT: 'Trả tiền', CANCEL: 'Hủy phiếu nhập', MANUAL: 'Sửa nợ' }
const payForm = reactive({ amount: 0, note: '' })
const paying = ref(false)

const ledger = usePagedList<SupplierLedgerRow>(() => `/suppliers/${currentId.value}/ledger`, { size: 20 }, { sync: false })
const receipts = usePagedList<ReceiptRow>('/receipts', { size: 20 }, {
  sync: false, params: (q) => ({ ...q, supplierId: currentId.value, status: 'POSTED,CANCELLED' }),
})

watch(currentId, async (id) => {
  current.value = null
  if (!id) return
  tab.value = 'ledger'
  Object.assign(payForm, { amount: 0, note: '' })
  try {
    current.value = (await http.get<Supplier>(`/suppliers/${id}`)).data
    ledger.set({ page: 1 })
    receipts.set({ page: 1 })
  } catch { closeDetail() }
}, { immediate: true })

function openDetail(s: Supplier) { router.push({ path: `/suppliers/${s.id}`, query: route.query }) }
function closeDetail() {
  if (String(window.history.state?.back ?? '').startsWith('/suppliers')) router.back()
  else router.replace({ path: '/suppliers', query: route.query })
}
async function refresh(s: Supplier) { current.value = s; ledger.set({ page: 1 }); list.load() }

async function pay() {
  const s = current.value!
  if (!(payForm.amount > 0)) return ElMessage.warning('Nhập số tiền trả nhà cung cấp')
  if (payForm.amount > Number(s.debt)) return ElMessage.warning(`Số tiền lớn hơn số đang nợ (${money(s.debt)})`)
  paying.value = true
  try {
    const { data } = await http.post<Supplier>(`/suppliers/${s.id}/payments`, payForm)
    ElMessage.success(`Đã ghi trả ${money(payForm.amount)}. Còn nợ ${money(data.debt)}`)
    Object.assign(payForm, { amount: 0, note: '' })
    await refresh(data)
  } finally {
    paying.value = false
  }
}
async function setDebt() {
  const s = current.value!
  const { value } = await ElMessageBox.prompt(`Đang nợ ${s.name}: ${money(s.debt)}. Nhập số nợ đúng (chỉ chữ số):`, 'Sửa số nợ nhà cung cấp', {
    inputValue: String(Number(s.debt)), inputPattern: /^\d+$/, inputErrorMessage: 'Chỉ nhập chữ số, ví dụ 1500000',
    confirmButtonText: 'Lưu số nợ', cancelButtonText: 'Hủy',
  })
  await refresh((await http.post<Supplier>(`/suppliers/${s.id}/debt-adjustments`, { debt: Number(value), note: 'Sửa số nợ' })).data)
  ElMessage.success('Đã sửa số nợ')
}
async function hide(s: Supplier) {
  await ElMessageBox.confirm(`Ẩn "${s.name}"? Phiếu nhập và sổ nợ cũ vẫn được giữ.`, 'Ẩn nhà cung cấp',
    { confirmButtonText: 'Ẩn', cancelButtonText: 'Không', type: 'warning', confirmButtonClass: 'el-button--danger' })
  await http.delete(`/suppliers/${s.id}`)
  closeDetail()
  list.load()
}

onMounted(list.load)
</script>

<template>
  <PageHeader title="Nhà cung cấp" subtitle="Công nợ phải trả: tăng khi ghi sổ phiếu nhập chưa trả đủ, giảm khi trả tiền.">
    <el-button type="primary" :icon="Plus" @click="openForm()">Thêm nhà cung cấp</el-button>
  </PageHeader>

  <div class="toolbar">
    <el-input v-model="search" :prefix-icon="Search" placeholder="Tên, SĐT hoặc địa chỉ" clearable class="search" aria-label="Tìm nhà cung cấp"
              @input="pushSearch" @clear="pushSearch" />
    <el-checkbox :model-value="!!query.debt" border @change="(v: any) => list.set({ debt: !!v })">Chỉ nhà cung cấp đang nợ</el-checkbox>
  </div>

  <div class="panel">
    <div v-if="summary" class="sumbar" aria-live="polite">
      <span><b class="num">{{ num(summary.count) }}</b> nhà cung cấp</span>
      <span>Đang nợ <b class="num">{{ num(summary.suppliersInDebt) }}</b> nhà cung cấp, tổng <b class="num tone-debt">{{ money(summary.totalDebt) }}</b></span>
    </div>
    <el-table v-loading="loading" :data="items" :default-sort="list.defaultSort()" row-class-name="clickable" @sort-change="list.onSort" @row-click="openDetail">
      <el-table-column prop="name" label="Nhà cung cấp" min-width="200" sortable="custom">
        <template #default="{ row }">
          <router-link :to="{ path: `/suppliers/${row.id}`, query: route.query }" class="link" @click.stop>{{ row.name }}</router-link>
          <div v-if="narrow && row.phone" class="muted small num">{{ row.phone }}</div>
        </template>
      </el-table-column>
      <el-table-column v-if="!narrow" label="SĐT" width="140"><template #default="{ row }"><span class="num">{{ row.phone }}</span></template></el-table-column>
      <el-table-column v-if="!narrow" prop="address" label="Địa chỉ" min-width="200" show-overflow-tooltip />
      <el-table-column prop="debt" label="Cửa hàng đang nợ" :width="narrow ? 140 : 170" align="right" sortable="custom">
        <template #default="{ row }">
          <span v-if="Number(row.debt) > 0" class="num tone-debt">{{ num(row.debt) }}</span>
          <span v-else class="muted">Không nợ</span>
        </template>
      </el-table-column>
      <template #empty>
        <EmptyState v-if="failed" error title="Không tải được danh sách"><el-button @click="list.load">Thử lại</el-button></EmptyState>
        <EmptyState v-else-if="filtered" title="Không có nhà cung cấp khớp bộ lọc">
          <el-button @click="() => { search = ''; list.set({ q: '', debt: false }) }">Bỏ lọc</el-button>
        </EmptyState>
        <EmptyState v-else title="Chưa có nhà cung cấp"><el-button type="primary" :icon="Plus" @click="openForm()">Thêm nhà cung cấp</el-button></EmptyState>
      </template>
    </el-table>
    <Pager :page="query.page" :size="query.size" :total="total" unit="nhà cung cấp" @change="list.set" />
  </div>

  <el-drawer :model-value="!!currentId" :title="current?.name ?? 'Nhà cung cấp'" size="min(720px, 100%)" @close="closeDetail">
    <template v-if="current">
      <div class="info">
        <p class="muted"><span class="num">{{ current.phone || 'Chưa có SĐT' }}</span><span v-if="current.address"> · {{ current.address }}</span></p>
        <div class="actions">
          <el-button size="small" @click="openForm(current)">Sửa thông tin</el-button>
          <el-button size="small" @click="setDebt">Sửa số nợ</el-button>
          <router-link to="/receipts/new"><el-button size="small" :icon="Van">Lập phiếu nhập</el-button></router-link>
        </div>
      </div>

      <section class="collect">
        <div>
          <div class="muted">{{ Number(current.debt) > 0 ? 'Cửa hàng đang nợ' : 'Công nợ' }}</div>
          <div class="debt num" :class="{ 'tone-debt': Number(current.debt) > 0 }">{{ money(current.debt) }}</div>
        </div>
        <form v-if="Number(current.debt) > 0" class="pay" @submit.prevent="pay">
          <label for="amt" class="lbl">Trả nhà cung cấp</label>
          <div class="pay-row">
            <NumberField id="amt" v-model="payForm.amount" kind="integer" :max="Number(current.debt)" :step="1000000" class="amt" />
            <el-button @click="payForm.amount = Number(current.debt)">Trả hết</el-button>
          </div>
          <el-input v-model="payForm.note" placeholder="Ghi chú: chuyển khoản, tiền mặt..." aria-label="Ghi chú trả tiền" />
          <el-button type="success" native-type="submit" :loading="paying" :disabled="!(payForm.amount > 0)">Ghi nhận trả {{ money(payForm.amount) }}</el-button>
        </form>
      </section>

      <el-tabs v-model="tab">
        <el-tab-pane name="ledger" :label="`Sổ công nợ (${ledger.total.value})`">
          <el-table v-loading="ledger.loading.value" :data="ledger.items.value" size="small" empty-text="Chưa có phát sinh">
            <el-table-column label="Thời gian" width="140"><template #default="{ row }"><span class="num">{{ dtShort(row.createdAt) }}</span></template></el-table-column>
            <el-table-column label="Loại" width="120"><template #default="{ row }">{{ typeLabel[row.type] }}</template></el-table-column>
            <el-table-column label="Phát sinh" width="124" align="right">
              <template #default="{ row }"><span class="num" :class="Number(row.amount) < 0 ? 'tone-money' : 'tone-debt'">{{ Number(row.amount) > 0 ? '+' : '' }}{{ num(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column label="Còn nợ" width="120" align="right"><template #default="{ row }"><span class="num">{{ num(row.balanceAfter) }}</span></template></el-table-column>
            <el-table-column label="Ghi chú" min-width="150">
              <template #default="{ row }">
                <router-link v-if="row.receiptId" :to="`/receipts/${row.receiptId}`" class="link">{{ row.note }}</router-link>
                <span v-else>{{ row.note }}</span>
              </template>
            </el-table-column>
          </el-table>
          <Pager compact :page="ledger.query.page" :size="ledger.query.size" :total="ledger.total.value" unit="dòng" @change="ledger.set" />
        </el-tab-pane>
        <el-tab-pane name="receipts" :label="`Phiếu nhập (${receipts.total.value})`">
          <el-table v-loading="receipts.loading.value" :data="receipts.items.value" size="small" empty-text="Chưa có phiếu nhập">
            <el-table-column label="Mã phiếu" width="110"><template #default="{ row }"><router-link :to="`/receipts/${row.id}`" class="link mono">{{ row.code }}</router-link></template></el-table-column>
            <el-table-column label="Ghi sổ" width="140"><template #default="{ row }"><span class="num">{{ dtShort(row.postedAt) }}</span></template></el-table-column>
            <el-table-column label="Tiền hàng" width="120" align="right"><template #default="{ row }"><span class="num">{{ num(row.total) }}</span></template></el-table-column>
            <el-table-column label="Đã trả" width="120" align="right"><template #default="{ row }"><span class="num">{{ num(row.paidAmount) }}</span></template></el-table-column>
            <el-table-column label="Trạng thái" min-width="90">
              <template #default="{ row }"><span :class="row.status === 'CANCELLED' ? 'tone-danger' : 'muted'">{{ row.status === 'CANCELLED' ? 'Đã hủy' : 'Đã ghi sổ' }}</span></template>
            </el-table-column>
          </el-table>
          <Pager compact :page="receipts.query.page" :size="receipts.query.size" :total="receipts.total.value" unit="phiếu" @change="receipts.set" />
        </el-tab-pane>
      </el-tabs>

      <div class="danger-zone"><el-button type="danger" plain size="small" @click="hide(current)">Ẩn nhà cung cấp</el-button></div>
    </template>
  </el-drawer>

  <el-dialog v-model="dialog.open" :title="dialog.id ? 'Sửa nhà cung cấp' : 'Thêm nhà cung cấp'" width="min(440px, 94vw)">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
      <el-form-item label="Tên nhà cung cấp" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="Số điện thoại"><el-input v-model="form.phone" type="tel" /></el-form-item>
      <el-form-item label="Địa chỉ"><el-input v-model="form.address" /></el-form-item>
      <el-form-item v-if="!dialog.id" label="Nợ ban đầu (₫)">
        <NumberField v-model="form.initialDebt" kind="integer" :step="1000000" />
        <div class="muted small">Số cửa hàng đang nợ nhà cung cấp trước khi dùng phần mềm.</div>
      </el-form-item>
      <button type="submit" hidden />
    </el-form>
    <template #footer>
      <el-button @click="dialog.open = false">Hủy</el-button>
      <el-button type="primary" :loading="dialog.saving" @click="save">Lưu nhà cung cấp</el-button>
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
