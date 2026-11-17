<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ShoppingCart, Wallet } from '@element-plus/icons-vue'
import http from '../api/http'
import type { OrderRow, Overview, Page } from '../api/types'
import { dt, num } from '../format'
import PageHeader from '../components/PageHeader.vue'
import StatCard from '../components/StatCard.vue'

// Thay man Home (menu nut) + phan so lieu dau trang cua ThongKe cu
const ov = ref<Overview | null>(null)
const router = useRouter()
const recent = ref<OrderRow[]>([])
const today = new Date().toLocaleDateString('vi-VN', { weekday: 'long', day: 'numeric', month: 'numeric', year: 'numeric' })

onMounted(async () => {
  const [a, b] = await Promise.all([
    http.get<Overview>('/reports/overview'),
    http.get<Page<OrderRow>>('/orders', { params: { status: 'PAID', size: 8 } }), // chi lay 8 dong, khong tai ca nghin hoa don
  ])
  ov.value = a.data
  recent.value = b.data.items
})
</script>

<template>
  <PageHeader title="Tổng quan" :subtitle="today">
    <router-link to="/customers?debt=1"><el-button :icon="Wallet">Thu nợ</el-button></router-link>
    <router-link to="/sale"><el-button type="primary" :icon="ShoppingCart">Bán hàng</el-button></router-link>
  </PageHeader>

  <template v-if="ov">
    <div class="grid4">
      <StatCard label="Doanh thu hôm nay" unit="₫" :value="ov.day.current" :previous="ov.day.previous" previous-label="Hôm qua" />
      <StatCard label="Tuần này (7 ngày qua)" unit="₫" :value="ov.week.current" :previous="ov.week.previous" previous-label="Tuần trước" />
      <StatCard label="Tháng này" unit="₫" :value="ov.month.current" :previous="ov.month.previous" previous-label="Tháng trước" />
      <StatCard label="Năm nay" unit="₫" :value="ov.year.current" :previous="ov.year.previous" previous-label="Năm trước" />
    </div>

    <div class="row2">
      <div class="debt panel">
        <div class="label">Khách đang nợ</div>
        <div class="big num tone-debt">{{ num(ov.totalDebt) }} <small>₫</small></div>
        <p class="muted">{{ ov.customersInDebt }} khách còn nợ</p>
        <router-link to="/customers?debt=1" class="link">Xem danh sách thu nợ</router-link>
      </div>
      <div class="mini-grid">
        <StatCard label="Hóa đơn tháng này" :value="ov.ordersThisMonth" />
        <StatCard label="Giá trị đơn hàng trung bình" :value="ov.avgOrderThisMonth" unit="₫">tổng thanh toán (gồm nợ cũ), tháng này</StatCard>
        <StatCard label="Khách mới tháng này" :value="ov.newCustomersThisMonth" />
        <StatCard label="Tỷ lệ khách quay lại" :value="ov.returningRate" unit="%">khách mua từ 2 lần / tổng số khách</StatCard>
      </div>
    </div>
  </template>
  <el-skeleton v-else :rows="6" animated />

  <section class="panel recent">
    <div class="recent-head">
      <h2>Hóa đơn gần đây</h2>
      <router-link to="/orders" class="link">Tất cả hóa đơn</router-link>
    </div>
    <el-table :data="recent" empty-text="Chưa có hóa đơn nào" row-class-name="clickable" @row-click="(r: OrderRow) => router.push(`/orders/${r.id}`)">
      <el-table-column label="Mã" width="130"><template #default="{ row }"><router-link :to="`/orders/${row.id}`" class="link mono" @click.stop>{{ row.code }}</router-link></template></el-table-column>
      <el-table-column label="Lúc" min-width="150"><template #default="{ row }">{{ dt(row.checkedOutAt) }}</template></el-table-column>
      <el-table-column label="Khách" min-width="160"><template #default="{ row }">{{ row.customerName || 'Khách lẻ' }}</template></el-table-column>
      <el-table-column label="Tiền hàng" min-width="130" align="right"><template #default="{ row }"><span class="num">{{ num(row.itemsTotal) }}</span></template></el-table-column>
      <el-table-column label="Còn nợ" min-width="130" align="right">
        <template #default="{ row }"><span v-if="Number(row.debtAfter) > 0" class="num tone-debt">{{ num(row.debtAfter) }}</span><span v-else class="muted">Đã trả đủ</span></template>
      </el-table-column>
    </el-table>
  </section>
</template>

<style scoped>
.grid4 { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-lg); margin-bottom: var(--space-lg); }
.row2 { display: grid; grid-template-columns: minmax(260px, 1fr) minmax(0, 2fr); gap: var(--space-lg); margin-bottom: var(--space-lg); }
.debt { padding: var(--space-xl); border-left: 4px solid var(--debt); display: flex; flex-direction: column; gap: var(--space-sm); }
.debt .label { color: var(--ink-muted); font-size: 14px; }
.debt .big { font-size: 32px; font-weight: 600; line-height: 1.15; }
.debt .big small { font-size: 16px; }
.debt p { margin: 0; }
.mini-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-lg); }
.link { color: var(--steel-800); font-weight: 500; font-size: 14px; text-decoration: none; }
.link:hover { text-decoration: underline; }
:deep(.clickable) { cursor: pointer; }
.recent { padding: var(--space-lg) var(--space-xl) var(--space-xl); }
.recent-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: var(--space-md); }
.recent-head h2 { font-size: 16px; }
@media (max-width: 1100px) { .grid4 { grid-template-columns: repeat(2, minmax(0, 1fr)); } .row2 { grid-template-columns: 1fr; } }
@media (max-width: 520px) { .grid4 { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 560px) { .grid4, .mini-grid { grid-template-columns: 1fr; } }
</style>
