<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import http from '../api/http'
import type { DayRevenue, TopProduct } from '../api/types'
import { isoDate, num, qty } from '../format'
import PageHeader from '../components/PageHeader.vue'
import StatCard from '../components/StatCard.vue'
import BarChart from '../components/BarChart.vue'

// Thay man ThongKe cu (chon khoang ngay, doanh thu, top san pham)
const now = new Date()
const presets = [
  { key: 'today', label: 'Hôm nay', range: () => [now, now] },
  { key: '7d', label: '7 ngày', range: () => [new Date(now.getTime() - 6 * 864e5), now] },
  { key: 'month', label: 'Tháng này', range: () => [new Date(now.getFullYear(), now.getMonth(), 1), now] },
  { key: 'lastMonth', label: 'Tháng trước', range: () => [new Date(now.getFullYear(), now.getMonth() - 1, 1), new Date(now.getFullYear(), now.getMonth(), 0)] },
  { key: 'year', label: 'Năm nay', range: () => [new Date(now.getFullYear(), 0, 1), now] },
]
const preset = ref('month')
const range = ref<[string, string]>(['', ''])
const rows = ref<DayRevenue[]>([])
const top = ref<TopProduct[]>([])
const loading = ref(false)

const totals = computed(() => rows.value.reduce(
  (a, r) => ({ orders: a.orders + r.orders, revenue: a.revenue + Number(r.revenue), collected: a.collected + Number(r.collected) }),
  { orders: 0, revenue: 0, collected: 0 }))
const chart = computed(() => rows.value.map((r) => ({ label: r.day.slice(8, 10) + '/' + r.day.slice(5, 7), value: Number(r.revenue) })))

function applyPreset(k: string) {
  const p = presets.find((x) => x.key === k)!
  const [a, b] = p.range()
  range.value = [isoDate(a), isoDate(b)]
  load()
}

async function load() {
  if (!range.value?.[0]) return
  loading.value = true
  try {
    const params = { from: range.value[0], to: range.value[1] }
    const [a, b] = await Promise.all([
      http.get<DayRevenue[]>('/reports/revenue', { params }),
      http.get<TopProduct[]>('/reports/top-products', { params }),
    ])
    rows.value = a.data
    top.value = b.data
  } finally {
    loading.value = false
  }
}

function onPick() {
  preset.value = ''
  load()
}

onMounted(() => applyPreset('month'))
</script>

<template>
  <PageHeader title="Thống kê" subtitle="Chỉ tính hóa đơn đã chốt. Doanh thu là tiền hàng bán ra; thực thu là tiền khách đã trả trên các hóa đơn đó." />

  <div class="toolbar">
    <el-radio-group v-model="preset" @change="(k: any) => applyPreset(k)">
      <el-radio-button v-for="p in presets" :key="p.key" :value="p.key">{{ p.label }}</el-radio-button>
    </el-radio-group>
    <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" format="DD/MM/YYYY" range-separator="–"
                    start-placeholder="Từ ngày" end-placeholder="Đến ngày" :clearable="false" @change="onPick" />
  </div>

  <div v-loading="loading">
    <div class="grid3">
      <StatCard label="Số hóa đơn" :value="totals.orders" />
      <StatCard label="Doanh thu bán ra" :value="totals.revenue" unit="₫" />
      <StatCard label="Tiền thực thu" :value="totals.collected" unit="₫" tone="money">
        Chênh lệch ghi nợ: <span class="num tone-debt">{{ num(totals.revenue - totals.collected) }}</span>
      </StatCard>
    </div>

    <div class="row">
      <section class="panel panel-pad">
        <h2>Doanh thu theo ngày <span class="muted">(triệu đồng)</span></h2>
        <BarChart :data="chart" caption="Doanh thu theo ngày" />
        <details class="table-alt">
          <summary>Xem dạng bảng</summary>
          <el-table :data="rows" size="small">
            <el-table-column prop="day" label="Ngày" />
            <el-table-column prop="orders" label="Số HĐ" width="80" align="right" />
            <el-table-column label="Doanh thu" align="right"><template #default="{ row }"><span class="num">{{ num(row.revenue) }}</span></template></el-table-column>
            <el-table-column label="Thực thu" align="right"><template #default="{ row }"><span class="num">{{ num(row.collected) }}</span></template></el-table-column>
          </el-table>
        </details>
      </section>
      <section class="panel panel-pad">
        <h2>Bán chạy nhất</h2>
        <ol v-if="top.length" class="top">
          <li v-for="(t, i) in top" :key="t.productName">
            <span class="rank num">{{ i + 1 }}</span>
            <span class="name">{{ t.productName }}<small class="muted">{{ qty(t.totalQuantity) }} đơn vị</small></span>
            <span class="num">{{ num(t.revenue) }}</span>
          </li>
        </ol>
        <p v-else class="muted">Chưa có số liệu</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.grid3 { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-lg); margin-bottom: var(--space-lg); }
.row { display: grid; grid-template-columns: minmax(0, 3fr) minmax(280px, 2fr); gap: var(--space-lg); }
h2 { font-size: 16px; margin-bottom: var(--space-lg); }
h2 .muted { font-weight: 400; font-size: 13px; }
.table-alt { margin-top: var(--space-lg); font-size: 14px; }
.table-alt summary { cursor: pointer; color: var(--steel-800); }
.top { list-style: none; margin: 0; padding: 0; }
.top li { display: grid; grid-template-columns: 28px 1fr auto; gap: var(--space-md); align-items: center; padding: var(--space-md) 0; border-bottom: 1px solid var(--steel-100); font-size: 14px; }
.rank { color: var(--ink-muted); }
.name small { display: block; font-size: 12px; }
@media (max-width: 1000px) { .row, .grid3 { grid-template-columns: 1fr; } }
</style>
