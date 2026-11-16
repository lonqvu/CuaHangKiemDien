<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Printer, Search } from '@element-plus/icons-vue'
import http from '../api/http'
import type { CancelEffect, Order } from '../api/types'
import { dateOnly, dt, money, num, qty } from '../format'
import PageHeader from '../components/PageHeader.vue'
import { openPdf } from '../api/pdf'

// Thay OrderHistory + OrderDetailDialog cu
const status = ref<'PAID' | 'CANCELLED'>('PAID')
const rows = ref<Order[]>([])
const q = ref('')
const loading = ref(false)
const range = ref<[string, string] | null>(null)
const detail = ref<Order | null>(null)

const filtered = computed(() => {
  const k = q.value.trim().toLowerCase()
  if (!k) return rows.value
  return rows.value.filter((o) =>
    [o.code, o.customerName, o.customerPhone, o.customerAddress, o.staffName, o.note].some((v) => v?.toLowerCase().includes(k)))
})
const totals = computed(() => filtered.value.reduce(
  (a, o) => ({ goods: a.goods + Number(o.itemsTotal), paid: a.paid + Number(o.paidAmount) }), { goods: 0, paid: 0 }))

async function load() {
  loading.value = true
  try {
    const params: Record<string, string> = { status: status.value }
    if (range.value) { params.from = range.value[0]; params.to = range.value[1] }
    rows.value = (await http.get<Order[]>('/orders', { params })).data
  }
  finally { loading.value = false }
}

async function cancel(o: Order) {
  // Xem truoc: no khach giam bao nhieu, cua hang phai tra lai bao nhieu tien mat (khach da tra no cua hoa don nay)
  const e = (await http.get<CancelEffect>(`/orders/${o.id}/cancel-preview`)).data
  const lines = [`Hóa đơn ${o.code} sẽ chuyển sang "Đã hủy" (không xóa dữ liệu).`]
  if (o.customerId) {
    if (Number(e.debtReversal) > 0) lines.push(`Nợ của ${o.customerName}: ${money(e.debtBefore)} → ${money(e.debtAfter)}.`)
    else if (Number(e.debtReversal) < 0) lines.push(`Nợ của ${o.customerName} tăng lại: ${money(e.debtBefore)} → ${money(e.debtAfter)} (hóa đơn này đã trừ bớt nợ cũ).`)
    if (Number(e.refund) > 0) lines.push(`Khách đã trả nợ của hóa đơn này nên cửa hàng phải TRẢ LẠI KHÁCH ${money(e.refund)} tiền mặt.`)
  }
  await ElMessageBox.confirm(lines.join('\n\n'), 'Hủy hóa đơn', {
    confirmButtonText: 'Hủy hóa đơn', cancelButtonText: 'Không', type: 'warning', customClass: 'pre-line',
  })
  const { data } = await http.post<Order>(`/orders/${o.id}/cancel`)
  detail.value = null
  load()
  if (Number(data.refundAmount) > 0) {
    // bat buoc bam xac nhan: viec tra tien mat nam ngoai phan mem, de khong bi quen
    await ElMessageBox.alert(`Trả lại ${data.customerName} ${money(data.refundAmount)} tiền mặt.\n\nNợ của khách đã về 0. Số tiền này được ghi trên hóa đơn ${data.code} và trong sổ công nợ.`,
      `Đã hủy ${data.code}: cần trả lại khách`, { confirmButtonText: 'Đã trả tiền cho khách', type: 'warning', customClass: 'pre-line', showClose: false })
  } else {
    ElMessage.success(`Đã hủy ${data.code}`)
  }
}

// In lai hoa don bang mau Jasper cu (Bill.jrxml)
function print(o: Order) { openPdf(`/orders/${o.id}/invoice.pdf`) }

onMounted(load)
</script>

<template>
  <PageHeader title="Hóa đơn" subtitle="Tất cả hóa đơn đã thanh toán, mới nhất trước. Hủy hóa đơn sẽ hoàn công nợ, không xóa dữ liệu." />

  <div class="toolbar">
    <el-radio-group v-model="status" @change="load">
      <el-radio-button value="PAID">Đã thanh toán</el-radio-button>
      <el-radio-button value="CANCELLED">Đã hủy</el-radio-button>
    </el-radio-group>
    <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" format="DD/MM/YYYY" range-separator="–"
                    start-placeholder="Từ ngày" end-placeholder="Đến ngày" style="max-width: 280px" @change="load" />
    <el-input v-model="q" :prefix-icon="Search" placeholder="Tìm mã, khách, SĐT, nhân viên, ghi chú" clearable style="max-width: 320px" aria-label="Tìm hóa đơn" />
    <span class="grow" />
    <span class="muted">{{ filtered.length }} hóa đơn · tiền hàng <b class="num">{{ num(totals.goods) }}</b> · đã thu <b class="num tone-money">{{ num(totals.paid) }}</b></span>
  </div>

  <div class="panel">
    <!-- Cot giong bang OrderHistory cu -->
    <el-table v-loading="loading" :data="filtered" empty-text="Không có hóa đơn" row-class-name="clickable" @row-click="(r: Order) => (detail = r)">
      <el-table-column label="Mã HĐ" width="112"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
      <el-table-column label="Ngày" width="96"><template #default="{ row }">{{ dateOnly(row.createdAt) }}</template></el-table-column>
      <el-table-column label="Khách hàng" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.customerName || 'Khách lẻ' }}</template>
      </el-table-column>
      <el-table-column label="SĐT" width="116"><template #default="{ row }"><span class="num">{{ row.customerPhone }}</span></template></el-table-column>
      <el-table-column label="Tiền hàng" width="112" align="right"><template #default="{ row }"><span class="num">{{ num(row.itemsTotal) }}</span></template></el-table-column>
      <el-table-column label="Nợ cũ" width="112" align="right"><template #default="{ row }"><span class="num">{{ num(row.previousDebt) }}</span></template></el-table-column>
      <el-table-column label="Tổng thanh toán" width="130" align="right"><template #default="{ row }"><span class="num">{{ num(row.totalPayable) }}</span></template></el-table-column>
      <el-table-column label="Khách trả" width="112" align="right"><template #default="{ row }"><span class="num">{{ num(row.paidAmount) }}</span></template></el-table-column>
      <el-table-column label="Còn lại" width="112" align="right">
        <template #default="{ row }"><span class="num" :class="{ 'tone-debt': Number(row.debtAfter) > 0 }">{{ num(row.debtAfter) }}</span></template>
      </el-table-column>
    </el-table>
  </div>

  <el-drawer :model-value="!!detail" :title="detail ? `Hóa đơn ${detail.code}` : ''" size="min(640px, 100%)" @close="detail = null">
    <template v-if="detail">
      <dl class="meta">
        <div><dt>Trạng thái</dt><dd><el-tag :type="detail.status === 'PAID' ? 'success' : 'info'">{{ detail.status === 'PAID' ? 'Đã thanh toán' : 'Đã hủy' }}</el-tag></dd></div>
        <div><dt>Ngày lập / chốt</dt><dd>{{ dateOnly(detail.createdAt) }} · {{ dt(detail.checkedOutAt) }}</dd></div>
        <div><dt>Khách</dt><dd>{{ detail.customerName || 'Khách lẻ' }}<br v-if="detail.customerPhone" /><span class="muted">{{ detail.customerPhone }}</span></dd></div>
        <div v-if="detail.customerAddress"><dt>Địa chỉ</dt><dd>{{ detail.customerAddress }}</dd></div>
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
        <div class="b"><dt>Tổng thanh toán</dt><dd class="num">{{ num(detail.totalPayable) }}</dd></div>
        <div><dt>Khách trả</dt><dd class="num tone-money">{{ num(detail.paidAmount) }}</dd></div>
        <div class="b"><dt>Còn lại</dt><dd class="num" :class="{ 'tone-debt': Number(detail.debtAfter) > 0 }">{{ num(detail.debtAfter) }}</dd></div>
        <div v-if="Number(detail.refundAmount) > 0" class="b refund"><dt>Đã trả lại khách khi hủy</dt><dd class="num">{{ num(detail.refundAmount) }}</dd></div>
      </dl>
    </template>
    <template #footer>
      <el-button v-if="detail?.status === 'PAID'" type="danger" plain @click="cancel(detail!)">Hủy hóa đơn</el-button>
      <el-button type="primary" :icon="Printer" @click="print(detail!)">In hóa đơn</el-button>
    </template>
  </el-drawer>

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
.sum .refund { color: var(--danger); }
</style>
