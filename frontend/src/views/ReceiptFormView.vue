<script setup lang="ts">
import NumberField from '../components/NumberField.vue'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Plus, Delete, Check } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Page, Product, ProductUnit, Receipt, Supplier } from '../api/types'
import { dtShort, highlight, money, num, qty, stockUnit } from '../format'
import EmptyState from '../components/EmptyState.vue'
import { useNarrow } from '../composables/useNarrow'
import { useRemotePick } from '../composables/useRemotePick'

/*
 * Lap / xem phieu nhap. Phieu nhap (DRAFT) sua tu do; Ghi so -> cong kho, tinh lai gia von, ghi no NCC (khong sua duoc nua, chi huy).
 * Moi dong: chon don vi nhap (don vi goc hoac don vi quy doi cua san pham), so luong + gia theo don vi do.
 * So luong theo don vi goc tu tinh = SL nhap x ty le, sua duoc theo so do thuc te (vd can 1.000 kg ton nhung do ra 312 m).
 */
const route = useRoute()
const router = useRouter()
const narrow = useNarrow()

interface Line {
  key: number; productId: number | null; productName: string; baseUnit: string; units: ProductUnit[]
  inputUnit: string; inputQty: number; inputPrice: number; qtyOverride: number | null | undefined
}
let seq = 0
const blank = (): Line => ({ key: ++seq, productId: null, productName: '', baseUnit: '', units: [], inputUnit: '', inputQty: 1, inputPrice: 0, qtyOverride: undefined })

const receipt = ref<Receipt | null>(null)
const loading = ref(false)
const busy = ref(false)
const head = reactive({ supplierId: null as number | null, note: '', paidAmount: 0 })
const lines = ref<Line[]>([blank()])
const editable = computed(() => !receipt.value || receipt.value.status === 'DRAFT')
const id = computed(() => (route.params.id ? Number(route.params.id) : null))

const ratioOf = (l: Line) => (l.inputUnit && l.inputUnit !== l.baseUnit ? Number(l.units.find((u) => u.unit === l.inputUnit)?.ratio ?? 1) : 1)
const baseQty = (l: Line) => (l.qtyOverride && l.qtyOverride > 0 ? l.qtyOverride : Math.round((l.inputQty || 0) * ratioOf(l) * 1000) / 1000)
const lineTotal = (l: Line) => Math.round((l.inputQty || 0) * (l.inputPrice || 0))
const unitCost = (l: Line) => (baseQty(l) > 0 ? lineTotal(l) / baseQty(l) : 0)
const total = computed(() => lines.value.reduce((a, l) => a + (l.productId ? lineTotal(l) : 0), 0))
const debt = computed(() => Math.max(0, total.value - (head.paidAmount || 0)))

// ---------- chon san pham / NCC: cung o tim voi man Ban hang (Enter chon ket qua dau) ----------
// Luong phim: NCC Enter -> San pham Enter -> SL nhap Enter -> Gia nhap Enter -> San pham dong moi
const productRefs = new Map<number, any>()
const qtyRefs = new Map<number, any>()
const priceRefs = new Map<number, any>()
const setRef = (m: Map<number, any>, key: number) => (el: any) => { if (el) m.set(key, el); else m.delete(key) }
let activeLine: Line | null = null
const setActive = (l: Line) => { activeLine = l }
const { options: productOptions, loading: productLoading, query: productQuery, search: searchProducts, onEnter: productEnter } = useRemotePick<Product>(
  async (q) => (await http.get<Page<Product>>('/products', { params: { q, size: 30 } })).data.items,
  async (first) => {
    const l = activeLine
    if (!l) return
    l.productId = first.id
    productRefs.get(l.key)?.blur()
    await pickProduct(l, first.id)
  },
)
function onProductEnter(l: Line) {
  activeLine = l
  productEnter()
}
// Enter o Gia nhap: sang o San pham cua dong trong ke tiep (luon co 1 dong trong o cuoi)
async function nextLine(l: Line) {
  const i = lines.value.indexOf(l)
  if (i === lines.value.length - 1) lines.value.push(blank())
  await nextTick()
  productRefs.get(lines.value[i + 1].key)?.focus()
}
function firstEmptyProduct() {
  const l = lines.value.find((x) => !x.productId)
  if (l) nextTick(() => productRefs.get(l.key)?.focus())
}
function optionsFor(l: Line) {
  const own = l.productId && !productOptions.value.some((p) => p.id === l.productId)
    ? [{ id: l.productId, name: l.productName, unit: l.baseUnit } as Product] : []
  return [...own, ...productOptions.value]
}
async function pickProduct(l: Line, pid: number) {
  const p = optionsFor(l).find((x) => x.id === pid)
  l.productName = p?.name ?? ''
  l.baseUnit = p?.unit ?? ''
  l.inputUnit = l.baseUnit
  l.qtyOverride = undefined
  if (lines.value[lines.value.length - 1] === l) lines.value.push(blank()) // luon co 1 dong trong o cuoi de nhap tiep
  await nextTick()
  qtyRefs.get(l.key)?.focus() // chon xong -> sang SL nhap (boi den san de go de)
  l.units = (await http.get<ProductUnit[]>(`/products/${pid}/units`)).data
}

const supSelect = ref()
const { options: suppliers, loading: supLoading, search: searchSuppliers, onEnter: onSupEnter } = useRemotePick<Supplier>(
  async (q) => (await http.get<Page<Supplier>>('/suppliers', { params: { q, size: 20 } })).data.items,
  (first) => { head.supplierId = first.id; supSelect.value?.blur(); firstEmptyProduct() },
  { keep: (): Supplier | undefined => suppliers.value.find((s: Supplier) => s.id === head.supplierId) },
)
const newSup = reactive({ open: false, name: '', phone: '', address: '' })
const newSupForm = ref<FormInstance>()
async function createSupplier() {
  if (!(await newSupForm.value?.validate().catch(() => false))) return
  const { data } = await http.post<Supplier>('/suppliers', { name: newSup.name, phone: newSup.phone, address: newSup.address })
  suppliers.value = [data, ...suppliers.value]
  head.supplierId = data.id
  Object.assign(newSup, { open: false, name: '', phone: '', address: '' })
}

// ---------- tai / luu ----------
function fromReceipt(r: Receipt) {
  receipt.value = r
  Object.assign(head, { supplierId: r.supplierId, note: r.note ?? '', paidAmount: Number(r.paidAmount) })
  if (r.supplierId && !suppliers.value.some((s) => s.id === r.supplierId))
    suppliers.value = [{ id: r.supplierId, name: r.supplierName ?? '', phone: null, address: null, debt: 0 }, ...suppliers.value]
  lines.value = r.lines.map((l) => ({
    key: ++seq, productId: l.productId, productName: l.productName, baseUnit: l.unit ?? '', units: [],
    inputUnit: l.inputUnit ?? l.unit ?? '', inputQty: Number(l.inputQty), inputPrice: Number(l.inputPrice),
    qtyOverride: Math.abs(Number(l.qty) - Number(l.inputQty) * Number(l.ratio)) > 0.0005 ? Number(l.qty) : undefined,
  }))
  if (r.status === 'DRAFT') {
    lines.value.forEach(async (l) => { l.units = (await http.get<ProductUnit[]>(`/products/${l.productId}/units`)).data })
    lines.value.push(blank())
  }
}

async function load() {
  if (!id.value) {
    receipt.value = null
    Object.assign(head, { supplierId: null, note: '', paidAmount: 0 })
    lines.value = [blank()]
    const pid = Number(route.query.product) // tu the kho bam "Nhap hang"
    if (pid) {
      const { data } = await http.get<{ stock: { productId: number; name: string; unit: string } }>(`/stock/${pid}`)
      productOptions.value = [{ id: pid, name: data.stock.name, unit: data.stock.unit } as Product, ...productOptions.value]
      await pickProduct(lines.value[0], pid)
    }
    return
  }
  loading.value = true
  try { fromReceipt((await http.get<Receipt>(`/receipts/${id.value}`)).data) }
  finally { loading.value = false }
}
watch(id, load)

function body() {
  const filled = lines.value.filter((l) => l.productId)
  return {
    supplierId: head.supplierId, note: head.note, paidAmount: head.paidAmount || 0,
    lines: filled.map((l) => ({ productId: l.productId, inputUnit: l.inputUnit, inputQty: l.inputQty, inputPrice: l.inputPrice, qty: l.qtyOverride || null })),
  }
}

async function save(silent = false) {
  if (!lines.value.some((l) => l.productId)) { ElMessage.warning('Thêm ít nhất 1 mặt hàng'); return null }
  busy.value = true
  try {
    const r = id.value
      ? (await http.put<Receipt>(`/receipts/${id.value}`, body())).data
      : (await http.post<Receipt>('/receipts', body())).data
    if (!silent) ElMessage.success('Đã lưu phiếu nháp')
    if (!id.value) await router.replace(`/receipts/${r.id}`)
    else fromReceipt(r)
    return r
  } finally {
    busy.value = false
  }
}

async function post() {
  const n = lines.value.filter((l) => l.productId).length
  if (!n) return ElMessage.warning('Thêm ít nhất 1 mặt hàng')
  if (!head.supplierId && debt.value > 0) return ElMessage.warning('Chọn nhà cung cấp để ghi nợ, hoặc bấm "Trả đủ"')
  await ElMessageBox.confirm(
    `Ghi sổ ${n} mặt hàng, tổng ${money(total.value)}${debt.value > 0 ? `, ghi nợ nhà cung cấp ${money(debt.value)}` : ''}? Sau khi ghi sổ không sửa được, chỉ hủy được.`,
    'Ghi sổ phiếu nhập', { confirmButtonText: 'Ghi sổ', cancelButtonText: 'Xem lại', type: 'info' })
  const saved = await save(true)
  if (!saved) return
  busy.value = true
  try {
    const { data } = await http.post<Receipt>(`/receipts/${saved.id}/post`)
    fromReceipt(data)
    ElMessage.success(`Đã ghi sổ ${data.code}. Tồn kho và giá vốn đã cập nhật.`)
  } finally {
    busy.value = false
  }
}

async function removeDraft() {
  await ElMessageBox.confirm('Xóa phiếu nháp này? Không ảnh hưởng tồn kho.', 'Xóa phiếu nháp',
    { confirmButtonText: 'Xóa nháp', cancelButtonText: 'Không', type: 'warning', confirmButtonClass: 'el-button--danger' })
  await http.delete(`/receipts/${id.value}`)
  ElMessage.success('Đã xóa phiếu nháp')
  router.push('/receipts?status=DRAFT')
}

async function cancelReceipt() {
  const r = receipt.value!
  await ElMessageBox.confirm(`Hủy ${r.code}? Kho sẽ trừ lại đúng số đã nhập và công nợ nhà cung cấp được hoàn. Phiếu vẫn được giữ ở trạng thái "Đã hủy".`,
    'Hủy phiếu nhập', { confirmButtonText: 'Hủy phiếu', cancelButtonText: 'Không hủy', type: 'warning', confirmButtonClass: 'el-button--danger' })
  fromReceipt((await http.post<Receipt>(`/receipts/${r.id}/cancel`)).data)
  ElMessage.success(`Đã hủy ${r.code}`)
}

onMounted(() => { searchProducts(''); searchSuppliers(''); load() })
</script>

<template>
  <div v-loading="loading" class="wrap">
    <header class="top">
      <div>
        <router-link to="/receipts" class="back">← Nhập hàng</router-link>
        <h1>{{ receipt?.code ? `Phiếu nhập ${receipt.code}` : 'Phiếu nhập mới' }}</h1>
        <p class="muted">
          <template v-if="!receipt">Chưa lưu. Mã phiếu được cấp khi ghi sổ.</template>
          <template v-else-if="receipt.status === 'DRAFT'">Phiếu nháp, lập lúc {{ dtShort(receipt.createdAt) }} bởi {{ receipt.createdBy }}. Chưa ảnh hưởng tồn kho.</template>
          <template v-else-if="receipt.status === 'POSTED'">Đã ghi sổ lúc {{ dtShort(receipt.postedAt) }} bởi {{ receipt.createdBy }}.</template>
          <template v-else>Đã hủy lúc {{ dtShort(receipt.cancelledAt) }}. Tồn kho và công nợ đã hoàn lại.</template>
        </p>
      </div>
      <el-tag v-if="receipt" :type="receipt.status === 'POSTED' ? 'success' : receipt.status === 'CANCELLED' ? 'danger' : 'info'" effect="plain" size="large">
        {{ receipt.status === 'POSTED' ? 'Đã ghi sổ' : receipt.status === 'CANCELLED' ? 'Đã hủy' : 'Nháp' }}
      </el-tag>
    </header>

    <section class="panel panel-pad grid">
      <div class="f">
        <div class="row-between">
          <label for="sup">Nhà cung cấp</label>
          <el-button v-if="editable" link type="primary" @click="newSup.open = true">+ Nhà cung cấp mới</el-button>
        </div>
        <el-select id="sup" ref="supSelect" v-model="head.supplierId" filterable remote clearable default-first-option :debounce="0"
                   :remote-method="searchSuppliers" :loading="supLoading" :disabled="!editable"
                   placeholder="Gõ tên nhà cung cấp, Enter chọn (bắt buộc nếu nhập chịu)"
                   @keydown.enter.capture="onSupEnter" @change="(v: number | null) => v && firstEmptyProduct()">
          <el-option v-for="s in suppliers" :key="s.id" :value="s.id" :label="s.name">
            <div class="opt"><span>{{ s.name }} <span class="muted">{{ s.phone }}</span></span>
              <span v-if="Number(s.debt) > 0" class="num tone-debt">nợ {{ num(s.debt) }}</span></div>
          </el-option>
        </el-select>
      </div>
      <div class="f">
        <div class="row-between"><label for="note">Ghi chú</label></div>
        <el-input id="note" v-model="head.note" :disabled="!editable" placeholder="Số hóa đơn NCC, xe giao, người nhận..." />
      </div>
    </section>

    <section class="panel">
      <el-table :data="lines" row-key="key" class="lines">
        <el-table-column label="Sản phẩm" :min-width="narrow && !editable ? 130 : 230">
          <template #default="{ row }">
            <div v-if="editable" @keydown.enter.capture="onProductEnter(row)">
              <el-select :ref="setRef(productRefs, row.key)" v-model="row.productId" filterable remote default-first-option :debounce="0"
                         :remote-method="searchProducts" :loading="productLoading" placeholder="Gõ tên, không cần dấu, Enter chọn"
                         aria-label="Sản phẩm" @focus="setActive(row)" @change="(v: number) => pickProduct(row, v)">
                <el-option v-for="p in optionsFor(row)" :key="p.id" :value="p.id" :label="p.name">
                  <div class="opt">
                    <span><template v-for="(seg, i) in highlight(p.name, productQuery)" :key="i"><mark v-if="seg.m">{{ seg.t }}</mark><template v-else>{{ seg.t }}</template></template></span>
                    <span class="muted num">tồn {{ p.onHand == null ? '—' : qty(p.onHand) }} {{ stockUnit(p.unit) }}</span>
                  </div>
                </el-option>
              </el-select>
            </div>
            <span v-else>{{ row.productName }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="editable || !narrow" label="Đơn vị nhập" width="120">
          <template #default="{ row }">
            <el-select v-if="editable && row.productId" v-model="row.inputUnit" aria-label="Đơn vị nhập" @change="row.qtyOverride = undefined">
              <el-option :value="row.baseUnit" :label="stockUnit(row.baseUnit)" />
              <el-option v-for="u in row.units" :key="u.unit" :value="u.unit" :label="u.unit" />
            </el-select>
            <span v-else>{{ row.inputUnit }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="editable || !narrow" label="SL nhập" min-width="120" align="right">
          <template #default="{ row }">
            <NumberField v-if="editable" :ref="setRef(qtyRefs, row.key)" v-model="row.inputQty" kind="decimal" class="n" aria-label="Số lượng nhập" :disabled="!row.productId"
                         @keydown.enter.prevent="priceRefs.get(row.key)?.focus()" />
            <span v-else class="num">{{ qty(row.inputQty) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="editable || !narrow" label="Giá nhập" min-width="130" align="right">
          <template #default="{ row }">
            <NumberField v-if="editable" :ref="setRef(priceRefs, row.key)" v-model="row.inputPrice" kind="integer" class="n" aria-label="Giá nhập" :suffix="row.inputUnit ? `/${row.inputUnit}` : undefined" :disabled="!row.productId"
                         @keydown.enter.prevent="nextLine(row)" />
            <span v-else class="num">{{ num(row.inputPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Vào kho (đơn vị gốc)" :width="narrow && !editable ? 110 : 170" align="right">
          <template #default="{ row }">
            <template v-if="row.productId">
              <NumberField v-if="editable && row.inputUnit !== row.baseUnit" v-model="row.qtyOverride" kind="decimal" nullable class="n"
                           :placeholder="qty(baseQty(row))" aria-label="Số lượng thực tế theo đơn vị gốc" />
              <span v-else class="num">{{ qty(baseQty(row)) }}</span>
              <span class="muted small">&nbsp;{{ stockUnit(row.baseUnit) }}</span>
              <div v-if="row.inputUnit !== row.baseUnit" class="muted small">
                {{ row.qtyOverride ? 'đo thực tế' : `= ${qty(row.inputQty)} × ${qty(ratioOf(row))}` }}
              </div>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="Thành tiền" :width="narrow && !editable ? 110 : 130" align="right">
          <template #default="{ row }">
            <template v-if="row.productId">
              <b class="num">{{ num(lineTotal(row)) }}</b>
              <div class="muted small num">{{ num(Math.round(unitCost(row))) }} ₫/{{ stockUnit(row.baseUnit) }}</div>
            </template>
          </template>
        </el-table-column>
        <el-table-column v-if="editable" width="56" align="center">
          <template #default="{ row, $index }">
            <el-button v-if="row.productId" text type="danger" :icon="Delete" :aria-label="`Bỏ ${row.productName}`" @click="lines.splice($index, 1)" />
          </template>
        </el-table-column>
        <template #empty><EmptyState title="Phiếu không có mặt hàng" /></template>
      </el-table>
      <div v-if="editable" class="addrow">
        <el-button :icon="Plus" @click="lines.push(blank())">Thêm dòng</el-button>
        <span class="muted small">Đơn vị quy đổi (kg, bó, cuộn...) khai báo ở màn Sản phẩm.</span>
      </div>
    </section>

    <section class="panel panel-pad pay">
      <dl class="sum">
        <div class="total"><dt>Tổng tiền hàng</dt><dd class="num">{{ num(total) }}</dd></div>
        <div>
          <dt><label for="paid">Đã trả nhà cung cấp</label></dt>
          <dd>
            <NumberField v-if="editable" id="paid" v-model="head.paidAmount" kind="integer" :step="100000" class="n paid" />
            <span v-else class="num tone-money">{{ num(head.paidAmount) }}</span>
          </dd>
        </div>
        <div v-if="editable" class="quick">
          <el-button size="small" @click="head.paidAmount = total">Trả đủ</el-button>
          <el-button size="small" @click="head.paidAmount = 0">Ghi nợ hết</el-button>
        </div>
        <div class="remain" :class="{ owe: debt > 0 }"><dt>{{ debt > 0 ? 'Ghi nợ nhà cung cấp' : 'Còn nợ' }}</dt><dd class="num">{{ num(debt) }}</dd></div>
      </dl>
      <div class="actions">
        <template v-if="editable">
          <el-button v-if="id" type="danger" plain @click="removeDraft">Xóa nháp</el-button>
          <span class="grow" />
          <el-button :loading="busy" @click="save()">Lưu nháp</el-button>
          <el-button type="primary" size="large" :icon="Check" :loading="busy" @click="post">Ghi sổ, nhập kho</el-button>
        </template>
        <template v-else-if="receipt?.status === 'POSTED'">
          <el-button type="danger" plain @click="cancelReceipt">Hủy phiếu</el-button>
          <span class="grow" />
          <router-link to="/receipts/new"><el-button type="primary" :icon="Plus">Lập phiếu khác</el-button></router-link>
        </template>
      </div>
    </section>

    <el-dialog v-model="newSup.open" title="Thêm nhà cung cấp" width="min(420px, 94vw)">
      <el-form ref="newSupForm" :model="newSup" label-position="top" @submit.prevent="createSupplier">
        <el-form-item label="Tên nhà cung cấp" prop="name" :rules="[{ required: true, message: 'Nhập tên nhà cung cấp', trigger: 'blur' }]">
          <el-input v-model="newSup.name" />
        </el-form-item>
        <el-form-item label="Số điện thoại"><el-input v-model="newSup.phone" type="tel" /></el-form-item>
        <el-form-item label="Địa chỉ"><el-input v-model="newSup.address" /></el-form-item>
        <button type="submit" hidden />
      </el-form>
      <template #footer>
        <el-button @click="newSup.open = false">Hủy</el-button>
        <el-button type="primary" @click="createSupplier">Lưu và chọn</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wrap { display: flex; flex-direction: column; gap: var(--space-lg); }
.top { display: flex; justify-content: space-between; align-items: flex-end; gap: var(--space-lg); flex-wrap: wrap; }
.top h1 { font-size: 22px; margin-top: var(--space-sm); }
.top p { margin: 2px 0 0; font-size: 14px; }
.back { font-size: 13px; color: var(--steel-800); text-decoration: none; }
.back:hover { text-decoration: underline; }
.grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: var(--space-lg) var(--space-xl); align-items: end; }
.row-between { min-height: 24px; }
.f { display: flex; flex-direction: column; gap: var(--space-sm); min-width: 0; }
.f label { font-size: 13px; font-weight: 500; color: #334155; }
.f .el-select { width: 100%; }
.row-between { display: flex; justify-content: space-between; align-items: flex-end; }
.opt { display: flex; justify-content: space-between; gap: var(--space-lg); }
.opt mark { background: #FEF3C7; color: inherit; font-weight: 600; border-radius: 2px; padding: 0 1px; }
.lines :deep(.el-select), .n { width: 100%; }
/* o nhap moi cot cung 1 hang ngang: can tren, chu phu (quy doi, gia von) nam duoi */
.lines :deep(td.el-table__cell) { vertical-align: top; }
.lines :deep(td.el-table__cell .cell) { min-height: 32px; line-height: 32px; }
.lines :deep(td.el-table__cell .cell .small) { line-height: 1.4; }
.n :deep(input) { text-align: right; font-family: var(--font-mono); }
.small { font-size: 12px; }
.addrow { display: flex; align-items: center; gap: var(--space-lg); padding: var(--space-md) var(--space-xl) var(--space-lg); flex-wrap: wrap; }
.pay { display: flex; flex-direction: column; gap: var(--space-lg); }
.sum { margin: 0 0 0 auto; width: min(420px, 100%); display: flex; flex-direction: column; }
.sum > div { display: flex; justify-content: space-between; align-items: center; gap: var(--space-md); min-height: 36px; }
.sum dt { font-size: 14px; color: #334155; }
.sum dd { margin: 0; }
.total dt { font-weight: 600; color: var(--ink); }
.total dd { font-size: 22px; font-weight: 600; }
.paid { width: 170px; }
.quick { justify-content: flex-end !important; gap: var(--space-sm) !important; min-height: 0 !important; margin: var(--space-sm) 0; }
.quick .el-button + .el-button { margin-left: 0; }
.remain { border-top: 1px solid var(--steel-200); }
.remain dd { font-size: 18px; font-weight: 600; }
.remain.owe dt, .remain.owe dd { color: var(--debt); }
.actions { display: flex; align-items: center; gap: var(--space-md); flex-wrap: wrap; border-top: 1px solid var(--steel-100); padding-top: var(--space-lg); }
.actions .grow { flex: 1; }
.actions .el-button + .el-button { margin-left: 0; }
@media (max-width: 760px) { .grid { grid-template-columns: 1fr; } }
</style>
