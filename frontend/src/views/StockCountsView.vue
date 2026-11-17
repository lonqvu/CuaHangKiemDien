<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Category, CountRow } from '../api/types'
import { dtShort, num } from '../format'
import { usePagedList } from '../composables/usePagedList'
import PageHeader from '../components/PageHeader.vue'
import Pager from '../components/Pager.vue'
import EmptyState from '../components/EmptyState.vue'

// Phieu kiem ke: tao phieu -> dem thuc te -> ghi so phan chenh lech. Phieu dau tien dung de nhap ton dau ky + gia von.
const router = useRouter()
const list = usePagedList<CountRow, { countByStatus: Record<string, number> }>('/stock-counts', { status: 'POSTED' })
const { query, items, total, summary, loading, failed } = list
const categories = ref<Category[]>([])

const dlg = reactive({ open: false, note: '', categoryId: null as number | null, saving: false })
function openCreate(opening = false) {
  Object.assign(dlg, { open: true, note: opening ? 'Tồn đầu kỳ' : `Kiểm kê ngày ${new Date().toLocaleDateString('vi-VN')}`, categoryId: null, saving: false })
}
async function create() {
  dlg.saving = true
  try {
    const { data } = await http.post<{ id: number }>('/stock-counts', { note: dlg.note, categoryId: dlg.categoryId })
    router.push(`/stock-counts/${data.id}`)
  } finally {
    dlg.saving = false
  }
}

onMounted(async () => {
  list.load()
  categories.value = (await http.get<Category[]>('/categories')).data
})
</script>

<template>
  <PageHeader title="Kiểm kê" subtitle="Đếm hàng thực tế, ghi sổ phần chênh lệch so với sổ sách. Lần đầu dùng phần mềm: lập phiếu kiểm kê để nhập tồn đầu kỳ và giá vốn.">
    <el-button type="primary" :icon="Plus" @click="openCreate()">Lập phiếu kiểm kê</el-button>
  </PageHeader>

  <div class="toolbar">
    <el-radio-group :model-value="query.status" aria-label="Trạng thái" @change="(v: any) => list.set({ status: v })">
      <el-radio-button value="POSTED">Đã ghi sổ ({{ num(summary?.countByStatus?.POSTED ?? 0) }})</el-radio-button>
      <el-radio-button value="DRAFT">Đang đếm ({{ num(summary?.countByStatus?.DRAFT ?? 0) }})</el-radio-button>
    </el-radio-group>
  </div>

  <div class="panel">
    <el-table v-loading="loading" :data="items" row-class-name="clickable" @row-click="(r: CountRow) => router.push(`/stock-counts/${r.id}`)">
      <el-table-column label="Mã phiếu" width="130">
        <template #default="{ row }"><router-link :to="`/stock-counts/${row.id}`" class="link mono" @click.stop>{{ row.code || 'Đang đếm' }}</router-link></template>
      </el-table-column>
      <el-table-column :label="query.status === 'DRAFT' ? 'Lập lúc' : 'Ghi sổ lúc'" width="150">
        <template #default="{ row }"><span class="num">{{ dtShort(row.postedAt || row.createdAt) }}</span></template>
      </el-table-column>
      <el-table-column prop="note" label="Ghi chú" min-width="200" />
      <el-table-column label="Đã đếm" min-width="140" align="right">
        <template #default="{ row }"><span class="num">{{ row.countedLines }} / {{ row.lineCount }}</span> <span class="muted small">mặt hàng</span></template>
      </el-table-column>
      <el-table-column prop="createdBy" label="Người lập" width="140" />
      <template #empty>
        <EmptyState v-if="failed" error title="Không tải được phiếu kiểm kê"><el-button @click="list.load">Thử lại</el-button></EmptyState>
        <EmptyState v-else-if="query.status === 'DRAFT'" title="Không có phiếu đang đếm" />
        <EmptyState v-else title="Chưa kiểm kê lần nào" hint="Bắt đầu bằng phiếu tồn đầu kỳ: đếm hàng đang có trong kho và nhập giá vốn.">
          <el-button type="primary" @click="openCreate(true)">Lập phiếu tồn đầu kỳ</el-button>
        </EmptyState>
      </template>
    </el-table>
    <Pager :page="query.page" :size="query.size" :total="total" unit="phiếu" @change="list.set" />
  </div>

  <el-dialog v-model="dlg.open" title="Lập phiếu kiểm kê" width="min(440px, 94vw)">
    <el-form label-position="top" @submit.prevent="create">
      <el-form-item label="Ghi chú"><el-input v-model="dlg.note" /></el-form-item>
      <el-form-item label="Kiểm kê loại hàng">
        <el-select v-model="dlg.categoryId" clearable placeholder="Tất cả sản phẩm đang bán" style="width: 100%">
          <el-option v-for="c in categories" :key="c.id" :value="c.id" :label="`${c.name} (${c.productCount})`" />
        </el-select>
      </el-form-item>
      <p class="muted small">Phiếu liệt kê sản phẩm đang bán. Mặt hàng nào không nhập số đếm sẽ được bỏ qua khi ghi sổ.</p>
      <button type="submit" hidden />
    </el-form>
    <template #footer>
      <el-button @click="dlg.open = false">Hủy</el-button>
      <el-button type="primary" :loading="dlg.saving" @click="create">Lập phiếu</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
:deep(.clickable) { cursor: pointer; }
.link { color: var(--steel-800); font-weight: 500; text-decoration: none; }
.link:hover { text-decoration: underline; }
.small { font-size: 12px; }
</style>
