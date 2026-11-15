<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Category, Product } from '../api/types'
import PageHeader from '../components/PageHeader.vue'

// Thay man Data (danh muc san pham) cu
const rows = ref<(Category & { count: number })[]>([])
const newName = ref('')
const editing = ref<number | null>(null)
const editName = ref('')

async function load() {
  const [c, p] = await Promise.all([http.get<Category[]>('/categories'), http.get<Product[]>('/products')])
  rows.value = c.data
    .map((x) => ({ ...x, count: p.data.filter((pp) => pp.categoryId === x.id).length }))
    .sort((a, b) => a.name.localeCompare(b.name, 'vi'))
}

async function add() {
  if (!newName.value.trim()) return
  await http.post('/categories', { name: newName.value })
  newName.value = ''
  load()
}

function startEdit(c: Category) {
  editing.value = c.id
  editName.value = c.name
}
async function saveEdit(c: Category) {
  if (!editName.value.trim()) return
  await http.put(`/categories/${c.id}`, { name: editName.value })
  editing.value = null
  load()
}

async function remove(c: Category & { count: number }) {
  await ElMessageBox.confirm(`Xóa loại "${c.name}"?`, 'Xóa loại hàng', { confirmButtonText: 'Xóa', cancelButtonText: 'Không', type: 'warning' })
  await http.delete(`/categories/${c.id}`)
  ElMessage.success('Đã xóa')
  load()
}

onMounted(load)
</script>

<template>
  <PageHeader title="Loại hàng" subtitle="Nhóm sản phẩm để lọc và thống kê. Chỉ xóa được loại không còn sản phẩm." />

  <div class="wrap">
    <form class="toolbar" @submit.prevent="add">
      <el-input v-model="newName" placeholder="Tên loại mới, VD: Nhôm hệ, Sắt hộp, Tôn" aria-label="Tên loại hàng mới" style="max-width: 360px" />
      <el-button type="primary" native-type="submit" :icon="Plus">Thêm loại</el-button>
    </form>

    <div class="panel">
      <el-table :data="rows" empty-text="Chưa có loại hàng">
        <el-table-column label="Tên loại" min-width="220">
          <template #default="{ row }">
            <form v-if="editing === row.id" class="inline" @submit.prevent="saveEdit(row)">
              <el-input v-model="editName" size="small" aria-label="Tên loại" autofocus />
              <el-button size="small" type="primary" native-type="submit">Lưu</el-button>
              <el-button size="small" @click="editing = null">Hủy</el-button>
            </form>
            <span v-else>{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Số sản phẩm" width="130" align="right"><template #default="{ row }"><span class="num">{{ row.count }}</span></template></el-table-column>
        <el-table-column width="140" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="startEdit(row)">Đổi tên</el-button>
            <el-button link type="danger" :disabled="row.count > 0" :title="row.count > 0 ? 'Còn sản phẩm thuộc loại này' : ''" @click="remove(row)">Xóa</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.wrap { max-width: 760px; }
.inline { display: flex; gap: var(--space-sm); }
</style>
