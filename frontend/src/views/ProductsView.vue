<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Category, Product } from '../api/types'
import { debounce, num } from '../format'
import { usePagedList } from '../composables/usePagedList'
import PageHeader from '../components/PageHeader.vue'
import Pager from '../components/Pager.vue'
import EmptyState from '../components/EmptyState.vue'

// Thay man Product cu. "Xoa" san pham = ngung ban (xoa mem): hoa don cu giu nguyen, ban lai duoc.
const list = usePagedList<Product>('/products', { q: '', categoryId: 0, active: true, sort: '' })
const { query, items, total, loading, failed } = list
const categories = ref<Category[]>([])

const search = ref(String(query.q))
const pushSearch = debounce(() => list.set({ q: search.value.trim() }), 300)
watch(() => query.q, (v) => { if (v !== search.value.trim()) search.value = String(v) })
const filtered = computed(() => !!query.q || query.categoryId !== 0)
function clearFilters() { search.value = ''; list.set({ q: '', categoryId: 0 }) }

async function loadCategories() { categories.value = (await http.get<Category[]>('/categories')).data }

const formRef = ref<FormInstance>()
const dialog = reactive({ open: false, id: null as number | null, saving: false })
const form = reactive({ name: '', categoryId: null as number | null, unit: '', price: 0 })
const rules: FormRules = {
  name: [{ required: true, message: 'Nhập tên sản phẩm', trigger: 'blur' }],
  unit: [{ required: true, message: 'Nhập đơn vị tính, ví dụ m, kg, cây', trigger: 'blur' }],
  price: [{ required: true, type: 'number', min: 0, message: 'Giá bán không được âm', trigger: 'blur' }],
}

function openForm(p?: Product) {
  dialog.id = p?.id ?? null
  Object.assign(form, p
    ? { name: p.name, categoryId: p.categoryId, unit: p.unit ?? '', price: Number(p.price) }
    : { name: '', categoryId: Number(query.categoryId) > 0 ? Number(query.categoryId) : null, unit: '', price: 0 })
  dialog.open = true
  nextTick(() => formRef.value?.clearValidate())
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  dialog.saving = true
  try {
    if (dialog.id) await http.put(`/products/${dialog.id}`, form)
    else await http.post('/products', form)
    dialog.open = false
    ElMessage.success(dialog.id ? 'Đã lưu sản phẩm' : `Đã thêm ${form.name}`)
    list.load()
    loadCategories()
  } finally {
    dialog.saving = false
  }
}

async function discontinue(p: Product) {
  await ElMessageBox.confirm(`Ngừng bán "${p.name}"? Sản phẩm không hiện ở màn Bán hàng nữa. Hóa đơn cũ giữ nguyên; bán lại được ở mục "Ngừng bán".`,
    'Ngừng bán sản phẩm', { confirmButtonText: 'Ngừng bán', cancelButtonText: 'Không', type: 'warning', confirmButtonClass: 'el-button--danger' })
  await http.delete(`/products/${p.id}`)
  ElMessage.success(`Đã ngừng bán ${p.name}`)
  list.load()
  loadCategories()
}

async function restore(p: Product) {
  await http.post(`/products/${p.id}/restore`)
  ElMessage.success(`Đã bán lại ${p.name}`)
  list.load()
  loadCategories()
}

onMounted(() => { list.load(); loadCategories() })
</script>

<template>
  <PageHeader title="Sản phẩm" subtitle="Giá ở đây là giá mặc định; khi bán vẫn sửa được đơn giá từng dòng.">
    <router-link to="/categories"><el-button>Quản lý loại hàng</el-button></router-link>
    <el-button type="primary" :icon="Plus" @click="openForm()">Thêm sản phẩm</el-button>
  </PageHeader>

  <div class="toolbar">
    <el-radio-group :model-value="query.active ? 'on' : 'off'" aria-label="Trạng thái bán" @change="(v: any) => list.set({ active: v === 'on' })">
      <el-radio-button value="on">Đang bán</el-radio-button>
      <el-radio-button value="off">Ngừng bán</el-radio-button>
    </el-radio-group>
    <el-input v-model="search" :prefix-icon="Search" placeholder="Tìm tên sản phẩm" clearable class="search" aria-label="Tìm sản phẩm"
              @input="pushSearch" @clear="pushSearch" />
    <el-select :model-value="Number(query.categoryId)" class="cat" aria-label="Lọc theo loại" @change="(v: number) => list.set({ categoryId: v })">
      <el-option label="Tất cả loại" :value="0" />
      <el-option v-for="c in categories" :key="c.id" :label="`${c.name} (${c.productCount})`" :value="c.id" />
      <el-option label="Chưa phân loại" :value="-1" />
    </el-select>
  </div>

  <div class="panel">
    <el-table v-loading="loading" :data="items" :default-sort="list.defaultSort()" @sort-change="list.onSort">
      <el-table-column prop="name" label="Tên sản phẩm" min-width="240" sortable="custom" />
      <el-table-column label="Loại" width="180"><template #default="{ row }">{{ row.categoryName || 'Chưa phân loại' }}</template></el-table-column>
      <el-table-column prop="unit" label="Đơn vị" width="110" sortable="custom" />
      <el-table-column prop="price" label="Giá bán" width="150" align="right" sortable="custom">
        <template #default="{ row }"><span class="num">{{ num(row.price) }}</span></template>
      </el-table-column>
      <el-table-column width="170" align="right">
        <template #default="{ row }">
          <template v-if="query.active">
            <el-button link type="primary" @click="openForm(row)">Sửa</el-button>
            <el-button link type="danger" @click="discontinue(row)">Ngừng bán</el-button>
          </template>
          <el-button v-else link type="primary" @click="restore(row)">Bán lại</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <EmptyState v-if="failed" error title="Không tải được danh sách sản phẩm" hint="Bấm Thử lại. Nếu vẫn lỗi, tải lại trang (F5) hoặc báo quản trị.">
          <el-button @click="list.load">Thử lại</el-button>
        </EmptyState>
        <EmptyState v-else-if="filtered" title="Không có sản phẩm khớp bộ lọc">
          <el-button @click="clearFilters">Bỏ lọc</el-button>
        </EmptyState>
        <EmptyState v-else-if="!query.active" title="Không có sản phẩm ngừng bán" />
        <EmptyState v-else title="Chưa có sản phẩm nào" hint="Thêm sản phẩm để chọn được ở màn Bán hàng.">
          <el-button type="primary" :icon="Plus" @click="openForm()">Thêm sản phẩm</el-button>
        </EmptyState>
      </template>
    </el-table>
    <Pager :page="query.page" :size="query.size" :total="total" unit="sản phẩm" @change="list.set" />
  </div>

  <el-dialog v-model="dialog.open" :title="dialog.id ? 'Sửa sản phẩm' : 'Thêm sản phẩm'" width="min(460px, 94vw)">
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
      </el-form-item>
      <button type="submit" hidden />
    </el-form>
    <template #footer>
      <el-button @click="dialog.open = false">Hủy</el-button>
      <el-button type="primary" :loading="dialog.saving" @click="save">{{ dialog.id ? 'Lưu sản phẩm' : 'Thêm sản phẩm' }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.search { max-width: 300px; flex: 1 1 200px; }
.cat { width: 220px; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-lg); }
.money-input :deep(input) { text-align: left; font-family: var(--font-mono); }
@media (max-width: 480px) { .two { grid-template-columns: 1fr; } }
</style>
