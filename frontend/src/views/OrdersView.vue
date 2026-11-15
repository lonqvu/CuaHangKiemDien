<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import http from '../api/http'
import type { Order } from '../api/types'
import { dt, money } from '../format'

const status = ref<'PAID' | 'CANCELLED'>('PAID')
const rows = ref<Order[]>([])
const detail = ref<Order | null>(null)

async function load() {
  rows.value = (await http.get<Order[]>('/orders', { params: { status: status.value } })).data
}

async function cancel(o: Order) {
  await ElMessageBox.confirm(
    `Hủy hóa đơn ${o.code}? Hóa đơn được giữ lại (trạng thái Đã hủy) và công nợ khách được hoàn lại.`, 'Xác nhận')
  await http.post(`/orders/${o.id}/cancel`)
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <el-radio-group v-model="status" style="margin-bottom: 12px" @change="load">
      <el-radio-button value="PAID">Đã thanh toán</el-radio-button>
      <el-radio-button value="CANCELLED">Đã hủy</el-radio-button>
    </el-radio-group>
    <el-table :data="rows" border>
      <el-table-column prop="code" label="Mã" width="130" />
      <el-table-column label="Thời gian" width="170"><template #default="{ row }">{{ dt(row.checkedOutAt) }}</template></el-table-column>
      <el-table-column prop="customerName" label="Khách" />
      <el-table-column prop="staffName" label="Nhân viên" width="140" />
      <el-table-column label="Tiền hàng" width="140"><template #default="{ row }">{{ money(row.itemsTotal) }}</template></el-table-column>
      <el-table-column label="Đã trả" width="140"><template #default="{ row }">{{ money(row.paidAmount) }}</template></el-table-column>
      <el-table-column label="Còn nợ" width="140"><template #default="{ row }">{{ money(row.debtAfter) }}</template></el-table-column>
      <el-table-column width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="detail = row">Chi tiết</el-button>
          <el-button v-if="row.status === 'PAID'" link type="danger" @click="cancel(row)">Hủy</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :model-value="!!detail" :title="`Hóa đơn ${detail?.code}`" width="720px" @close="detail = null">
      <el-table v-if="detail" :data="detail.items" border>
        <el-table-column prop="productName" label="Sản phẩm" />
        <el-table-column prop="quantity" label="SL" width="80" />
        <el-table-column prop="sheets" label="Tấm" width="70" />
        <el-table-column prop="factor" label="Hệ số" width="80" />
        <el-table-column label="Đơn giá" width="120"><template #default="{ row }">{{ money(row.unitPrice) }}</template></el-table-column>
        <el-table-column label="Thành tiền" width="130"><template #default="{ row }">{{ money(row.lineTotal) }}</template></el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>
