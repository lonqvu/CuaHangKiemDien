<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Printer, Search } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Order } from '../api/types'
import { dt, num, qty } from '../format'
import PageHeader from '../components/PageHeader.vue'
import InvoicePrint from '../components/InvoicePrint.vue'

// Thay OrderHistory + OrderDetailDialog cu
const status = ref<'PAID' | 'CANCELLED'>('PAID')
const rows = ref<Order[]>([])
const q = ref('')
const loading = ref(false)
const detail = ref<Order | null>(null)
const printing = ref<Order | null>(null)

const filtered = computed(() => {
  const k = q.value.trim().toLowerCase()
  if (!k) return rows.value
  return rows.value.filter((o) =>
    [o.code, o.customerName, o.customerPhone, o.staffName].some((v) => v?.toLowerCase().includes(k)))
})
const totals = computed(() => filtered.value.reduce(
  (a, o) => ({ goods: a.goods + Number(o.itemsTotal), paid: a.paid + Number(o.paidAmount) }), { goods: 0, paid: 0 }))

async function load() {
  loading.value = true
  try { rows.value = (await http.get<Order[]>('/orders', { params: { status: status.value } })).data }
  finally { loading.value = false }
}

async function cancel(o: Order) {
  await ElMessageBox.confirm(
    `Hóa đơn ${o.code} sẽ chuyển sang "Đã hủy" và công nợ của khách được hoàn lại. Không xóa dữ liệu.`,
    'Hủy hóa đơn', { confirmButtonText: 'Hủy hóa đơn', cancelButtonText: 'Không', type: 'warning' })
  await http.post(`/orders/${o.id}/cancel`)
  ElMessage.success(`Đã hủy ${o.code}`)
  detail.value = null
  load()
}

async function print(o: Order) {
  printing.value = o
  await nextTick()
  window.print()
}

onMounted(load)
</script>

<template>
  <PageHeader title="Hóa đơn" subtitle="100 hóa đơn gần nhất. Hủy hóa đơn sẽ hoàn công nợ, không xóa dữ liệu." />

  <div class="toolbar">
    <el-radio-group v-model="status" @change="load">
      <el-radio-button value="PAID">Đã thanh toán</el-radio-button>
      <el-radio-button value="CANCELLED">Đã hủy</el-radio-button>
    </el-radio-group>
    <el-input v-model="q" :prefix-icon="Search" placeholder="Tìm mã, khách, SĐT, nhân viên" clearable style="max-width: 320px" aria-label="Tìm hóa đơn" />
    <span class="grow" />
    <span class="muted">{{ filtered.length }} hóa đơn · tiền hàng <b class="num">{{ num(totals.goods) }}</b> · đã thu <b class="num tone-money">{{ num(totals.paid) }}</b></span>
  </div>

  <div class="panel">
    <el-table v-loading="loading" :data="filtered" empty-text="Không có hóa đơn" row-class-name="clickable" @row-click="(r: Order) => (detail = r)">
      <el-table-column label="Mã" width="128"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
      <el-table-column label="Lúc chốt" width="140"><template #default="{ row }">{{ dt(row.checkedOutAt) }}</template></el-table-column>
      <el-table-column label="Khách" min-width="160">
        <template #default="{ row }">{{ row.customerName || 'Khách lẻ' }} <span class="muted">{{ row.customerPhone }}</span></template>
      </el-table-column>
      <el-table-column prop="staffName" label="Nhân viên" width="130" />
      <el-table-column label="Tiền hàng" width="120" align="right"><template #default="{ row }"><span class="num">{{ num(row.itemsTotal) }}</span></template></el-table-column>
      <el-table-column label="Khách trả" width="120" align="right"><template #default="{ row }"><span class="num">{{ num(row.paidAmount) }}</span></template></el-table-column>
      <el-table-column label="Còn nợ" width="120" align="right">
        <template #default="{ row }"><span class="num" :class="{ 'tone-debt': Number(row.debtAfter) > 0 }">{{ num(row.debtAfter) }}</span></template>
      </el-table-column>
    </el-table>
  </div>

  <el-drawer :model-value="!!detail" :title="detail ? `Hóa đơn ${detail.code}` : ''" size="min(640px, 100%)" @close="detail = null">
    <template v-if="detail">
      <dl class="meta">
        <div><dt>Trạng thái</dt><dd><el-tag :type="detail.status === 'PAID' ? 'success' : 'info'">{{ detail.status === 'PAID' ? 'Đã thanh toán' : 'Đã hủy' }}</el-tag></dd></div>
        <div><dt>Lúc chốt</dt><dd>{{ dt(detail.checkedOutAt) }}</dd></div>
        <div><dt>Khách</dt><dd>{{ detail.customerName || 'Khách lẻ' }}<br v-if="detail.customerPhone" /><span class="muted">{{ detail.customerPhone }}</span></dd></div>
        <div><dt>Nhân viên</dt><dd>{{ detail.staffName }}</dd></div>
        <div v-if="detail.note"><dt>Ghi chú</dt><dd>{{ detail.note }}</dd></div>
      </dl>
      <el-table :data="detail.items" size="small">
        <el-table-column prop="productName" label="Mặt hàng" min-width="150" />
        <el-table-column label="Tổng SL" width="90" align="right"><template #default="{ row }"><span class="num">{{ qty(row.totalQuantity) }}</span></template></el-table-column>
        <el-table-column label="Đơn giá" width="100" align="right"><template #default="{ row }"><span class="num">{{ num(row.unitPrice) }}</span></template></el-table-column>
        <el-table-column label="Thành tiền" width="110" align="right"><template #default="{ row }"><span class="num">{{ num(row.lineTotal) }}</span></template></el-table-column>
      </el-table>
      <dl class="sum">
        <div><dt>Tiền hàng</dt><dd class="num">{{ num(detail.itemsTotal) }}</dd></div>
        <div><dt>Phí vận chuyển</dt><dd class="num">{{ num(detail.shippingFee) }}</dd></div>
        <div><dt>Nợ cũ</dt><dd class="num">{{ num(detail.previousDebt) }}</dd></div>
        <div class="b"><dt>Tổng phải trả</dt><dd class="num">{{ num(detail.totalPayable) }}</dd></div>
        <div><dt>Khách trả</dt><dd class="num tone-money">{{ num(detail.paidAmount) }}</dd></div>
        <div class="b"><dt>Còn nợ</dt><dd class="num" :class="{ 'tone-debt': Number(detail.debtAfter) > 0 }">{{ num(detail.debtAfter) }}</dd></div>
      </dl>
    </template>
    <template #footer>
      <el-button v-if="detail?.status === 'PAID'" type="danger" plain @click="cancel(detail!)">Hủy hóa đơn</el-button>
      <el-button type="primary" :icon="Printer" @click="print(detail!)">In hóa đơn</el-button>
    </template>
  </el-drawer>

  <InvoicePrint :order="printing" />
</template>

<style scoped>
:deep(.clickable) { cursor: pointer; }
.meta { margin: 0 0 var(--space-lg); display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-md) var(--space-xl); }
.meta dt { font-size: 13px; color: var(--ink-muted); }
.meta dd { margin: 0; }
.sum { margin: var(--space-lg) 0 0 auto; max-width: 320px; }
.sum div { display: flex; justify-content: space-between; padding: 4px 0; }
.sum dd { margin: 0; }
.sum .b { font-weight: 600; border-top: 1px solid var(--steel-200); }
</style>
