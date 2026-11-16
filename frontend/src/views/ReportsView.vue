<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import http from '../api/http'
import type { CategoryStat, CustomerStat, DayRevenue, MonthRevenue, TopCustomer, TopProduct } from '../api/types'
import { datePresets, num, qty } from '../format'
import PageHeader from '../components/PageHeader.vue'
import StatCard from '../components/StatCard.vue'
import BarChart from '../components/BarChart.vue'

/*
 * Thay man ThongKe cu (che do chon khoang ngay):
 * doanh thu trong khoang, khach moi, ty le khach quay lai, top 10 san pham (theo so luong / doanh thu),
 * top 10 khach than thiet (theo so don / tong tien), bieu do doanh thu theo ngay hoac theo thang trong nam,
 * phan bo theo loai hang.
 */
const presets = datePresets()
const preset = ref('month')
const range = ref<[string, string]>(['', ''])
const rows = ref<DayRevenue[]>([])
const months = ref<MonthRevenue[]>([])
const top = ref<TopProduct[]>([])
const topCus = ref<TopCustomer[]>([])
const cats = ref<CategoryStat[]>([])
const cus = ref<CustomerStat | null>(null)
const loading = ref(false)
const chartMode = ref<'day' | 'month'>('day')
const productSort = ref<'revenue' | 'quantity'>('revenue')
const customerSort = ref<'orders' | 'amount'>('orders')

const totals = computed(() => rows.value.reduce(
  (a, r) => ({ orders: a.orders + r.orders, revenue: a.revenue + Number(r.revenue), collected: a.collected + Number(r.collected) }),
  { orders: 0, revenue: 0, collected: 0 }))
// Khoang dai (> 62 ngay, vd "Nam nay") thi tu gop theo thang: 300 cot ngay khong doc duoc
const spanDays = computed(() => (range.value[0] ? (Date.parse(range.value[1]) - Date.parse(range.value[0])) / 864e5 + 1 : 0))
const byMonthAuto = computed(() => chartMode.value === 'day' && spanDays.value > 62)
const chart = computed(() => {
  if (chartMode.value === 'month') return months.value.map((m) => ({ label: `T${m.month}`, value: Number(m.revenue) }))
  if (!byMonthAuto.value) return rows.value.map((r) => ({ label: r.day.slice(8, 10) + '/' + r.day.slice(5, 7), value: Number(r.revenue) }))
  const g = new Map<string, number>()
  for (const r of rows.value) {
    const k = `T${Number(r.day.slice(5, 7))}/${r.day.slice(2, 4)}`
    g.set(k, (g.get(k) ?? 0) + Number(r.revenue))
  }
  return [...g].map(([label, value]) => ({ label, value }))
})
const granularity = computed(() => (chartMode.value === 'month' || byMonthAuto.value ? 'tháng' : 'ngày'))
const params = () => ({ from: range.value[0], to: range.value[1] })

function applyPreset(k: string) {
  const p = presets.find((x) => x.key === k)!
  range.value = [p.from, p.to]
  load()
}

async function loadTop() {
  top.value = (await http.get<TopProduct[]>('/reports/top-products', { params: { ...params(), sort: productSort.value } })).data
}
async function loadTopCus() {
  topCus.value = (await http.get<TopCustomer[]>('/reports/top-customers', { params: { ...params(), sort: customerSort.value } })).data
}

async function load() {
  if (!range.value?.[0]) return
  loading.value = true
  try {
    const [a, c, d] = await Promise.all([
      http.get<DayRevenue[]>('/reports/revenue', { params: params() }),
      http.get<CategoryStat[]>('/reports/categories', { params: params() }),
      http.get<CustomerStat>('/reports/customers', { params: params() }),
      loadTop(),
      loadTopCus(),
    ])
    rows.value = a.data
    cats.value = c.data
    cus.value = d.data
  } finally {
    loading.value = false
  }
}

function onPick() {
  preset.value = ''
  load()
}

onMounted(async () => {
  applyPreset('month')
  months.value = (await http.get<MonthRevenue[]>('/reports/revenue-by-month')).data
})
</script>

<template>
  <PageHeader title="Thống kê" subtitle="Chỉ tính hóa đơn đã chốt. Doanh thu = tổng thành tiền các mặt hàng (không gồm phí ship, nợ cũ), như màn Thống kê cũ." />

  <div class="toolbar">
    <el-radio-group v-model="preset" @change="(k: any) => applyPreset(k)">
      <el-radio-button v-for="p in presets" :key="p.key" :value="p.key">{{ p.label }}</el-radio-button>
    </el-radio-group>
    <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" format="DD/MM/YYYY" range-separator="–"
                    start-placeholder="Từ ngày" end-placeholder="Đến ngày" :clearable="false" @change="onPick" />
  </div>

  <div v-loading="loading">
    <div class="grid4">
      <StatCard label="Doanh thu" :value="totals.revenue" unit="₫" />
      <StatCard label="Số hóa đơn" :value="totals.orders" />
      <StatCard label="Khách hàng mới" :value="cus?.newCustomers ?? 0" />
      <StatCard label="Tỷ lệ khách quay lại" :value="cus?.returningRate ?? 0" unit="%">
        {{ cus?.returningCustomers ?? 0 }} / {{ cus?.totalCustomers ?? 0 }} khách mua từ 2 lần
      </StatCard>
    </div>

    <section class="panel panel-pad block">
      <div class="head">
        <h2>Doanh thu</h2>
        <el-radio-group v-model="chartMode" size="small">
          <el-radio-button value="day">{{ byMonthAuto ? 'Khoảng đã chọn (gộp theo tháng)' : 'Theo ngày (khoảng đã chọn)' }}</el-radio-button>
          <el-radio-button value="month">Theo tháng (năm nay)</el-radio-button>
        </el-radio-group>
      </div>
      <BarChart :data="chart" :granularity="granularity" :caption="`Doanh thu theo ${granularity}`" />
      <p class="muted small">Tiền thực thu trên các hóa đơn này: <b class="num tone-money">{{ num(totals.collected) }}</b>. Số này gồm cả tiền khách trả nợ cũ khi mua hàng, nên có thể lớn hơn doanh thu.</p>
    </section>

    <div class="row">
      <section class="panel panel-pad">
        <div class="head">
          <h2>Top 10 sản phẩm</h2>
          <el-radio-group v-model="productSort" size="small" @change="loadTop">
            <el-radio-button value="quantity">Số lượng bán</el-radio-button>
            <el-radio-button value="revenue">Doanh thu</el-radio-button>
          </el-radio-group>
        </div>
        <el-table :data="top" size="small" empty-text="Chưa có số liệu">
          <el-table-column type="index" label="#" width="40" />
          <el-table-column prop="productName" label="Sản phẩm" min-width="160" show-overflow-tooltip />
          <el-table-column label="Số lượng" width="100" align="right"><template #default="{ row }"><span class="num">{{ qty(row.totalQuantity) }}</span></template></el-table-column>
          <el-table-column label="Doanh thu" width="120" align="right"><template #default="{ row }"><span class="num">{{ num(row.revenue) }}</span></template></el-table-column>
        </el-table>
      </section>
      <section class="panel panel-pad">
        <div class="head">
          <h2>Top 10 khách thân thiết</h2>
          <el-radio-group v-model="customerSort" size="small" @change="loadTopCus">
            <el-radio-button value="orders">Số đơn hàng</el-radio-button>
            <el-radio-button value="amount">Tổng tiền</el-radio-button>
          </el-radio-group>
        </div>
        <el-table :data="topCus" size="small" empty-text="Chưa có số liệu">
          <el-table-column type="index" label="#" width="40" />
          <el-table-column prop="customerName" label="Khách hàng" min-width="150" show-overflow-tooltip />
          <el-table-column prop="orders" label="Số đơn" width="72" align="right" />
          <el-table-column label="Tổng tiền" width="130" align="right"><template #default="{ row }"><span class="num">{{ num(row.totalAmount) }}</span></template></el-table-column>
        </el-table>
        <p class="muted small">Tổng tiền = tổng "Tổng thanh toán" các hóa đơn (gồm cả nợ cũ), như app cũ.</p>
      </section>
    </div>

    <section class="panel panel-pad block">
      <h2>Theo loại hàng</h2>
      <el-table :data="cats" size="small" empty-text="Chưa có loại hàng">
        <el-table-column prop="category" label="Loại hàng" min-width="160" />
        <el-table-column prop="productCount" label="Số mặt hàng" width="110" align="right" />
        <el-table-column label="Số lượng bán" width="130" align="right"><template #default="{ row }"><span class="num">{{ qty(row.totalQuantity) }}</span></template></el-table-column>
        <el-table-column label="Doanh thu" width="150" align="right"><template #default="{ row }"><span class="num">{{ num(row.revenue) }}</span></template></el-table-column>
        <el-table-column label="Tỷ trọng" width="200">
          <template #default="{ row }">
            <div class="share"><span :style="{ width: `${totals.revenue ? (row.revenue / totals.revenue) * 100 : 0}%` }" /></div>
            <span class="num small">{{ totals.revenue ? ((row.revenue / totals.revenue) * 100).toFixed(1).replace('.', ',') : '0' }}%</span>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.grid4 { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-lg); margin-bottom: var(--space-lg); }
.row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: var(--space-lg); margin-bottom: var(--space-lg); }
.block { margin-bottom: var(--space-lg); }
.head { display: flex; justify-content: space-between; align-items: center; gap: var(--space-md); flex-wrap: wrap; margin-bottom: var(--space-lg); }
h2 { font-size: 16px; }
.block > h2 { margin-bottom: var(--space-lg); }
h2 .muted { font-weight: 400; font-size: 13px; }
.small { font-size: 13px; }
p.small { margin: var(--space-md) 0 0; }
.share { display: inline-block; width: 110px; height: 8px; background: var(--steel-100); border-radius: 4px; overflow: hidden; vertical-align: middle; margin-right: 8px; }
.share span { display: block; height: 100%; background: var(--steel-800); }
@media (max-width: 1100px) { .grid4 { grid-template-columns: repeat(2, minmax(0, 1fr)); } .row { grid-template-columns: minmax(0, 1fr); } }
</style>
