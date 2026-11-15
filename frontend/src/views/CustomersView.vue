<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Customer, LedgerRow } from '../api/types'
import { debounce, dt, money, num } from '../format'
import PageHeader from '../components/PageHeader.vue'

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
const form = reactive({ name: '', phone: '', address: '' })
const rules: FormRules = { name: [{ required: true, message: 'Nhập tên khách', trigger: 'blur' }] }

function openForm(c?: Customer) {
  dialog.id = c?.id ?? null
  Object.assign(form, { name: c?.name ?? '', phone: c?.phone ?? '', address: c?.address ?? '' })
  dialog.open = true
}
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (dialog.id) await http.put(`/customers/${dialog.id}`, form)
  else await http.post('/customers', form)
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
const payForm = reactive({ amount: 0, note: '' })
const paying = ref(false)
const typeLabel: Record<string, string> = { ORDER: 'Mua hàng', PAYMENT: 'Trả nợ', ADJUST: 'Điều chỉnh' }

async function open(c: Customer) {
  current.value = c
  Object.assign(payForm, { amount: 0, note: '' })
  ledger.value = (await http.get<LedgerRow[]>(`/customers/${c.id}/debt-ledger`)).data
}

async function collect() {
  const c = current.value!
  if (!(payForm.amount > 0)) return ElMessage.warning('Nhập số tiền khách trả')
  if (payForm.amount > Number(c.debt)) return ElMessage.warning('Số tiền lớn hơn số nợ hiện tại')
  paying.value = true
  try {
    const { data } = await http.post<Customer>(`/customers/${c.id}/debt-payments`, payForm)
    ElMessage.success(`Đã thu ${money(payForm.amount)}. Còn nợ ${money(data.debt)}`)
    await load()
    await open(data)
  } finally {
    paying.value = false
  }
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

  <el-drawer :model-value="!!current" :title="current?.name" size="min(620px, 100%)" @close="current = null">
    <template v-if="current">
      <div class="info">
        <p class="muted">{{ current.phone || 'Chưa có SĐT' }}<span v-if="current.address"> · {{ current.address }}</span></p>
        <div class="actions">
          <el-button size="small" @click="openForm(current)">Sửa thông tin</el-button>
          <el-button size="small" type="danger" plain @click="hide(current)">Ẩn khách</el-button>
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
            <el-input-number id="amt" v-model="payForm.amount" :min="0" :max="Number(current.debt)" :step="100000" :controls="false" class="amt" />
            <el-button @click="payForm.amount = Number(current.debt)">Trả hết</el-button>
          </div>
          <el-input v-model="payForm.note" placeholder="Ghi chú (tiền mặt, chuyển khoản...)" aria-label="Ghi chú thu nợ" />
          <el-button type="success" native-type="submit" :loading="paying">Ghi nhận thu {{ money(payForm.amount) }}</el-button>
        </form>
      </section>

      <h3 class="ledger-title">Sổ công nợ</h3>
      <el-table :data="ledger" size="small" empty-text="Chưa có phát sinh">
        <el-table-column label="Thời gian" width="130"><template #default="{ row }">{{ dt(row.createdAt) }}</template></el-table-column>
        <el-table-column label="Loại" width="100"><template #default="{ row }">{{ typeLabel[row.type] }}</template></el-table-column>
        <el-table-column label="Phát sinh" width="120" align="right">
          <template #default="{ row }"><span class="num" :class="Number(row.amount) < 0 ? 'tone-money' : 'tone-debt'">{{ Number(row.amount) > 0 ? '+' : '' }}{{ num(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="Còn nợ" width="120" align="right"><template #default="{ row }"><span class="num">{{ num(row.balanceAfter) }}</span></template></el-table-column>
        <el-table-column prop="note" label="Ghi chú" min-width="140" show-overflow-tooltip />
      </el-table>
    </template>
  </el-drawer>

  <el-dialog v-model="dialog.open" :title="dialog.id ? 'Sửa khách hàng' : 'Thêm khách hàng'" width="440px">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
      <el-form-item label="Tên khách" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="Số điện thoại"><el-input v-model="form.phone" inputmode="tel" /></el-form-item>
      <el-form-item label="Địa chỉ"><el-input v-model="form.address" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog.open = false">Hủy</el-button>
      <el-button type="primary" @click="save">Lưu</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
:deep(.clickable) { cursor: pointer; }
.info { display: flex; justify-content: space-between; align-items: center; gap: var(--space-md); flex-wrap: wrap; margin-bottom: var(--space-lg); }
.info p { margin: 0; }
.collect { display: grid; grid-template-columns: 1fr 1.4fr; gap: var(--space-xl); padding: var(--space-lg); background: var(--steel-50); border-radius: var(--radius-lg); margin-bottom: var(--space-xl); }
.debt { font-size: 26px; font-weight: 600; }
.pay { display: flex; flex-direction: column; gap: var(--space-sm); }
.pay-row { display: flex; gap: var(--space-sm); }
.amt { flex: 1; }
.amt :deep(input) { text-align: right; font-family: var(--font-mono); }
.lbl { font-size: 13px; font-weight: 500; }
.ledger-title { font-size: 15px; margin-bottom: var(--space-md); }
@media (max-width: 560px) { .collect { grid-template-columns: 1fr; } }
</style>
