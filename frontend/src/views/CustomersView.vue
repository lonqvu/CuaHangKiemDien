<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import type { Customer, LedgerRow } from '../api/types'
import { dt, money } from '../format'

const rows = ref<Customer[]>([])
const q = ref('')
const dialog = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ name: '', address: '', phone: '' })
const ledgerDialog = ref(false)
const ledger = ref<LedgerRow[]>([])
const current = ref<Customer | null>(null)

const typeLabel = { ORDER: 'Mua hàng', PAYMENT: 'Trả nợ', ADJUST: 'Điều chỉnh' }

async function load() {
  rows.value = (await http.get<Customer[]>('/customers', { params: { q: q.value } })).data
}

function openDialog(c?: Customer) {
  editingId.value = c?.id ?? null
  Object.assign(form, { name: c?.name ?? '', address: c?.address ?? '', phone: c?.phone ?? '' })
  dialog.value = true
}

async function save() {
  if (editingId.value) await http.put(`/customers/${editingId.value}`, form)
  else await http.post('/customers', form)
  dialog.value = false
  load()
}

async function remove(c: Customer) {
  await ElMessageBox.confirm(`Ẩn khách hàng "${c.name}"? Lịch sử mua và công nợ vẫn được giữ.`, 'Xác nhận')
  await http.delete(`/customers/${c.id}`)
  load()
}

async function showLedger(c: Customer) {
  current.value = c
  ledger.value = (await http.get<LedgerRow[]>(`/customers/${c.id}/debt-ledger`)).data
  ledgerDialog.value = true
}

async function payDebt(c: Customer) {
  const { value } = await ElMessageBox.prompt(`Nợ hiện tại: ${money(c.debt)}. Nhập số tiền khách trả:`, 'Thu nợ', {
    inputPattern: /^\d+(\.\d+)?$/, inputErrorMessage: 'Nhập số tiền hợp lệ',
  })
  await http.post(`/customers/${c.id}/debt-payments`, { amount: Number(value) })
  ElMessage.success('Đã ghi nhận thu nợ')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div style="display: flex; gap: 8px; margin-bottom: 12px">
      <el-input v-model="q" placeholder="Tìm tên / SĐT" style="width: 260px" @input="load" clearable />
      <el-button type="primary" @click="openDialog()">Thêm khách hàng</el-button>
    </div>
    <el-table :data="rows" border>
      <el-table-column prop="name" label="Tên" />
      <el-table-column prop="phone" label="SĐT" width="140" />
      <el-table-column prop="address" label="Địa chỉ" />
      <el-table-column label="Công nợ" width="150"><template #default="{ row }">{{ money(row.debt) }}</template></el-table-column>
      <el-table-column width="270">
        <template #default="{ row }">
          <el-button link type="primary" @click="showLedger(row)">Sổ nợ</el-button>
          <el-button link type="success" :disabled="row.debt <= 0" @click="payDebt(row)">Thu nợ</el-button>
          <el-button link type="primary" @click="openDialog(row)">Sửa</el-button>
          <el-button link type="danger" @click="remove(row)">Ẩn</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editingId ? 'Sửa khách hàng' : 'Thêm khách hàng'" width="440px">
      <el-form label-width="90px">
        <el-form-item label="Tên"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="SĐT"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="Địa chỉ"><el-input v-model="form.address" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="save">Lưu</el-button></template>
    </el-dialog>

    <el-dialog v-model="ledgerDialog" :title="`Sổ công nợ - ${current?.name}`" width="720px">
      <el-table :data="ledger" border max-height="420">
        <el-table-column label="Thời gian" width="170"><template #default="{ row }">{{ dt(row.createdAt) }}</template></el-table-column>
        <el-table-column label="Loại" width="110"><template #default="{ row }">{{ typeLabel[row.type as keyof typeof typeLabel] }}</template></el-table-column>
        <el-table-column label="Phát sinh" width="150"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
        <el-table-column label="Còn nợ" width="150"><template #default="{ row }">{{ money(row.balanceAfter) }}</template></el-table-column>
        <el-table-column prop="note" label="Ghi chú" />
      </el-table>
    </el-dialog>
  </div>
</template>
