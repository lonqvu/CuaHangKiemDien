<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import http from '../api/http'
import type { Category, Product } from '../api/types'
import { money } from '../format'

const rows = ref<Product[]>([])
const categories = ref<Category[]>([])
const q = ref('')
const dialog = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ name: '', categoryId: null as number | null, unit: '', price: 0 })

async function load() {
  rows.value = (await http.get<Product[]>('/products', { params: { q: q.value } })).data
  categories.value = (await http.get<Category[]>('/categories')).data
}

function openDialog(p?: Product) {
  editingId.value = p?.id ?? null
  Object.assign(form, p ? { name: p.name, categoryId: p.categoryId, unit: p.unit ?? '', price: p.price }
                        : { name: '', categoryId: null, unit: '', price: 0 })
  dialog.value = true
}

async function save() {
  if (editingId.value) await http.put(`/products/${editingId.value}`, form)
  else await http.post('/products', form)
  dialog.value = false
  load()
}

async function remove(p: Product) {
  await ElMessageBox.confirm(`Xóa sản phẩm "${p.name}"? (hóa đơn cũ vẫn giữ nguyên)`, 'Xác nhận')
  await http.delete(`/products/${p.id}`)
  load()
}

async function addCategory() {
  const { value } = await ElMessageBox.prompt('Tên loại hàng', 'Thêm loại hàng')
  await http.post('/categories', { name: value })
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div style="display: flex; gap: 8px; margin-bottom: 12px">
      <el-input v-model="q" placeholder="Tìm sản phẩm" style="width: 260px" @input="load" clearable />
      <el-button type="primary" @click="openDialog()">Thêm sản phẩm</el-button>
      <el-button @click="addCategory">Thêm loại hàng</el-button>
    </div>
    <el-table :data="rows" border>
      <el-table-column prop="name" label="Tên" />
      <el-table-column prop="categoryName" label="Loại" width="180" />
      <el-table-column prop="unit" label="ĐVT" width="100" />
      <el-table-column label="Giá" width="160"><template #default="{ row }">{{ money(row.price) }}</template></el-table-column>
      <el-table-column width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">Sửa</el-button>
          <el-button link type="danger" @click="remove(row)">Xóa</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editingId ? 'Sửa sản phẩm' : 'Thêm sản phẩm'" width="440px">
      <el-form label-width="90px">
        <el-form-item label="Tên"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="Loại">
          <el-select v-model="form.categoryId" clearable style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="ĐVT"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="Giá"><el-input-number v-model="form.price" :min="0" :step="1000" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="save">Lưu</el-button></template>
    </el-dialog>
  </div>
</template>
