<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search, Delete } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Category, Product, ProductUnit } from '../api/types'
import { debounce, num, qty } from '../format'
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
const form = reactive({ name: '', categoryId: null as number | null, unit: '', price: 0, minStock: 0 })
// Don vi quy doi khi nhap hang: 1 [unit] = ratio [don vi goc]
const units = ref<ProductUnit[]>([])
const rules: FormRules = {
  name: [{ required: true, message: 'Nhập tên sản phẩm', trigger: 'blur' }],
  unit: [{ required: true, message: 'Nhập đơn vị tính, ví dụ m, kg, cây', trigger: 'blur' }],
  price: [{ required: true, type: 'number', min: 0, message: 'Giá bán không được âm', trigger: 'blur' }],
}

async function openForm(p?: Product) {
  dialog.id = p?.id ?? null
  Object.assign(form, p
    ? { name: p.name, categoryId: p.categoryId, unit: p.unit ?? '', price: Number(p.price), minStock: Number(p.minStock ?? 0) }
    : { name: '', categoryId: Number(query.categoryId) > 0 ? Number(query.categoryId) : null, unit: '', price: 0, minStock: 0 })
  units.value = p ? (await http.get<ProductUnit[]>(`/products/${p.id}/units`)).data.map((u) => ({ unit: u.unit, ratio: Number(u.ratio) })) : []
  dialog.open = true
  nextTick(() => formRef.value?.clearValidate())
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  dialog.saving = true
  try {
    const filled = units.value.filter((u) => u.unit.trim())
    if (filled.some((u) => !(Number(u.ratio) > 0))) { ElMessage.warning('Nhập tỷ lệ quy đổi lớn hơn 0 cho mọi đơn vị'); return }
    const saved = dialog.id ? (await http.put<Product>(`/products/${dialog.id}`, form)).data : (await http.post<Product>('/products', form)).data
    await http.put(`/products/${saved.id}/units`, filled)
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
      <el-table-column prop="unit" label="Đơn vị" width="100" sortable="custom" />
      <el-table-column label="Tồn kho" width="130" align="right">
        <template #default="{ row }">
          <router-link v-if="row.onHand != null" :to="`/inventory/${row.id}`" class="stock num"
                       :class="{ low: Number(row.minStock) > 0 && Number(row.onHand) <= Number(row.minStock), neg: Number(row.onHand) < 0 }">{{ qty(row.onHand) }}</router-link>
        </template>
      </el-table-column>
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
      <div class="two">
        <el-form-item label="Giá bán (₫)" prop="price">
          <el-input-number v-model="form.price" :min="0" :step="1000" :controls="false" style="width: 100%" class="money-input" />
        </el-form-item>
        <el-form-item :label="`Tồn tối thiểu${form.unit ? ` (${form.unit})` : ''}`">
          <el-input-number v-model="form.minStock" :min="0" :controls="false" style="width: 100%" class="money-input" />
        </el-form-item>
      </div>
      <fieldset class="units">
        <legend>Đơn vị quy đổi khi nhập hàng</legend>
        <p class="muted small">Ví dụ tôn bán theo m, nhập theo kg: 1 kg = 0,318 m. Thép bán theo cây, nhập theo bó: 1 bó = 10 cây. Bán hàng vẫn theo đơn vị gốc.</p>
        <div v-for="(u, i) in units" :key="i" class="unit-row">
          <span>1</span>
          <el-input v-model="u.unit" placeholder="kg, bó, cuộn" aria-label="Tên đơn vị quy đổi" class="u-name" />
          <span>=</span>
          <el-input-number v-model="u.ratio" :min="0" :precision="6" :controls="false" aria-label="Tỷ lệ quy đổi" class="money-input u-ratio" />
          <span class="muted">{{ form.unit || 'đơn vị gốc' }}</span>
          <el-button text type="danger" :icon="Delete" :aria-label="`Bỏ đơn vị ${u.unit}`" @click="units.splice(i, 1)" />
        </div>
        <el-button size="small" :icon="Plus" @click="units.push({ unit: '', ratio: 1 })">Thêm đơn vị quy đổi</el-button>
      </fieldset>
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
.stock { color: var(--ink); text-decoration: none; }
.stock:hover { text-decoration: underline; }
.stock.low { color: var(--debt); font-weight: 600; }
.stock.neg { color: var(--danger); font-weight: 600; }
.units { border: 1px solid var(--steel-200); border-radius: var(--radius-md); padding: var(--space-md) var(--space-lg) var(--space-lg); margin: 0; }
.units legend { font-size: 14px; font-weight: 500; padding: 0 var(--space-sm); }
.units p { margin: 0 0 var(--space-md); }
.unit-row { display: flex; align-items: center; gap: var(--space-sm); margin-bottom: var(--space-sm); }
.u-name { width: 110px; }
.u-ratio { width: 120px; }
.small { font-size: 12px; }
@media (max-width: 480px) { .two { grid-template-columns: 1fr; } }
</style>
