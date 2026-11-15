<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import http from '../api/http'
import type { DayRevenue } from '../api/types'
import { money } from '../format'

const today = new Date()
const iso = (d: Date) => d.toISOString().slice(0, 10)
const range = ref<[string, string]>([iso(new Date(today.getFullYear(), today.getMonth(), 1)), iso(today)])
const rows = ref<DayRevenue[]>([])
const top = ref<{ productName: string; totalQuantity: number; revenue: number }[]>([])

const totals = computed(() => ({
  orders: rows.value.reduce((a, r) => a + r.orders, 0),
  revenue: rows.value.reduce((a, r) => a + r.revenue, 0),
  collected: rows.value.reduce((a, r) => a + r.collected, 0),
}))

async function load() {
  const params = { from: range.value[0], to: range.value[1] }
  rows.value = (await http.get<DayRevenue[]>('/reports/revenue', { params })).data
  top.value = (await http.get('/reports/top-products', { params })).data
}

onMounted(load)
</script>

<template>
  <div>
    <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="Từ ngày"
                    end-placeholder="Đến ngày" @change="load" style="margin-bottom: 16px" />
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="8"><el-statistic title="Số hóa đơn" :value="totals.orders" /></el-col>
      <el-col :span="8"><el-statistic title="Doanh thu bán ra" :value="totals.revenue" /></el-col>
      <el-col :span="8"><el-statistic title="Tiền thực thu" :value="totals.collected" /></el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :span="12">
        <h4>Theo ngày</h4>
        <el-table :data="rows" border>
          <el-table-column prop="day" label="Ngày" />
          <el-table-column prop="orders" label="Số đơn" width="90" />
          <el-table-column label="Doanh thu"><template #default="{ row }">{{ money(row.revenue) }}</template></el-table-column>
          <el-table-column label="Thực thu"><template #default="{ row }">{{ money(row.collected) }}</template></el-table-column>
        </el-table>
      </el-col>
      <el-col :span="12">
        <h4>Top 10 sản phẩm</h4>
        <el-table :data="top" border>
          <el-table-column prop="productName" label="Sản phẩm" />
          <el-table-column prop="totalQuantity" label="SL" width="100" />
          <el-table-column label="Doanh thu"><template #default="{ row }">{{ money(row.revenue) }}</template></el-table-column>
        </el-table>
      </el-col>
    </el-row>
  </div>
</template>
