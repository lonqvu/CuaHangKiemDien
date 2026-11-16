<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Category, Product } from '../api/types'
import { debounce, num } from '../format'
import PageHeader from '../components/PageHeader.vue'

// Thay man Product cu
const rows = ref<Product[]>([])
const categories = ref<Category[]>([])
const q = ref('')
const cat = ref<number | ''>('')
const loading = ref(false)

const shown = computed(() => (cat.value === '' ? rows.value : rows.value.filter((p) => p.categoryId === cat.value)))

async function load() {
  loading.value = true
  try {
    const [p, c] = await Promise.all([
      http.get<Product[]>('/products', { params: { q: q.value } }),
      http.get<Category[]>('/categories'),
    ])
    rows.value = p.data
    categories.value = c.data
  } finally {
    loading.value = false
  }
}
const search = debounce(load)

const formRef = ref<FormInstance>()
const dialog = reactive({ open: false, id: null as number | null })
const form = reactive({ name: '', categoryId: null as number | null, unit: '', price: 0 })
const rules: FormRules = {
  name: [{ required: true, message: 'Nhập tên sản phẩm', trigger: 'blur' }],
  unit: [{ required: true, message: 'Bạn chưa nhập đơn vị cho sản phẩm', trigger: 'blur' }],
  price: [{ required: true, type: 'number', min: 0, message: 'Giá không hợp lệ', trigger: 'blur' }],
}

function openForm(p?: Product) {
  dialog.id = p?.id ?? null
  Object.assign(form, p
    ? { name: p.name, categoryId: p.categoryId, unit: p.unit ?? '', price: Number(p.price) }
    : { name: '', categoryId: cat.value === '' ? null : cat.value, unit: '', price: 0 })
  dialog.open = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (dialog.id) await http.put(`/products/${dialog.id}`, form)
  else await http.post('/products', form)
  dialog.open = false
  ElMessage.success('Đã lưu sản phẩm')
  load()
}

async function remove(p: Product) {
  await ElMessageBox.confirm(`Ngừng bán "${p.name}"? Hóa đơn cũ vẫn giữ nguyên tên và giá lúc bán.`, 'Xóa sản phẩm',
    { confirmButtonText: 'Xóa', cancelButtonText: 'Không', type: 'warning' })
  await http.delete(`/products/${p.id}`)
  load()
}

onMounted(load)
</script>

<template>
  <PageHeader title="Sản phẩm" :subtitle="`${rows.length} mặt hàng đang bán`">
    <router-link to="/categories"><el-button>Quản lý loại hàng</el-button></router-link>
    <el-button type="primary" :icon="Plus" @click="openForm()">Thêm sản phẩm</el-button>
  </PageHeader>

  <div class="toolbar">
    <el-input v-model="q" :prefix-icon="Search" placeholder="Tìm tên sản phẩm" clearable style="max-width: 300px" aria-label="Tìm sản phẩm" @input="search" />
    <el-select v-model="cat" placeholder="Tất cả loại" style="width: 200px" aria-label="Lọc theo loại">
      <el-option label="Tất cả loại" value="" />
      <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
    </el-select>
  </div>

  <div class="panel">
    <el-table v-loading="loading" :data="shown" empty-text="Không có sản phẩm">
      <el-table-column prop="name" label="Tên sản phẩm" min-width="220" />
      <el-table-column label="Loại" width="180"><template #default="{ row }">{{ row.categoryName || '—' }}</template></el-table-column>
      <el-table-column prop="unit" label="Đơn vị" width="100" />
      <el-table-column label="Giá bán" width="140" align="right"><template #default="{ row }"><span class="num">{{ num(row.price) }}</span></template></el-table-column>
      <el-table-column width="130" align="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">Sửa</el-button>
          <el-button link type="danger" @click="remove(row)">Xóa</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-dialog v-model="dialog.open" :title="dialog.id ? 'Sửa sản phẩm' : 'Thêm sản phẩm'" width="460px">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
      <el-form-item label="Tên sản phẩm" prop="name"><el-input v-model="form.name" placeholder="VD: Tôn lạnh 0,4 ly" /></el-form-item>
      <div class="two">
        <el-form-item label="Loại hàng">
          <el-select v-model="form.categoryId" clearable placeholder="Chưa phân loại" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Đơn vị tính" prop="unit"><el-input v-model="form.unit" placeholder="m, kg, cây, tấm" /></el-form-item>
      </div>
      <el-form-item label="Giá bán (₫)" prop="price">
        <el-input-number v-model="form.price" :min="0" :step="1000" :controls="false" style="width: 100%" class="money-input" />
        <span v-if="form.price >= 1000" class="echo">= {{ num(form.price) }} ₫</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog.open = false">Hủy</el-button>
      <el-button type="primary" @click="save">Lưu</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.two { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-lg); }
.money-input :deep(input) { text-align: left; font-family: var(--font-mono); }
</style>
