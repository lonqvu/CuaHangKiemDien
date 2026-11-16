<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Plus, Edit, Delete, Printer, DocumentAdd } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Category, Customer, Order, OrderItem, Product } from '../api/types'
import { debounce, highlight, money, num, previewLine, qty } from '../format'
import NumberField from '../components/NumberField.vue'
import { openPdf } from '../api/pdf'
import { useAuth } from '../stores/auth'

const auth = useAuth()

const DRAFT_KEY = 'sale.draftId'

const order = ref<Order | null>(null)
const done = ref<Order | null>(null) // hoa don vua chot, de in
const products = ref<Product[]>([])
const customers = ref<Customer[]>([])
const customer = ref<Customer | null>(null)
const busy = ref(false)
const loadError = ref(false)
const productSelect = ref()
const qtyField = ref()

const line = reactive({ productId: null as number | null, quantity: 1, sheets: 0, factor: 0, unitPrice: 0 })
const pay = reactive({ shippingFee: 0, paidAmount: 0, note: '', walkInName: '', walkInPhone: '' })

const picked = computed(() => products.value.find((p) => p.id === line.productId) ?? null)

// App cu: go so tam khac 0 thi he so tu dien 1.08 (ProductDialog); man sua dong (ModifyProduct) dien 1
watch(() => line.sheets, (v, old) => { if (v && v !== old) line.factor = 1.08 })
const preview = computed(() => previewLine(line.quantity || 0, line.sheets || 0, line.factor || 0, line.unitPrice || 0))

// ---------- hoa don nhap ----------
async function startNew() {
  const { data } = await http.post<Order>('/orders')
  order.value = data
  sessionStorage.setItem(DRAFT_KEY, String(data.id))
  done.value = null
  customer.value = null
  Object.assign(pay, { shippingFee: 0, paidAmount: 0, note: '', walkInName: '', walkInPhone: '' })
  resetLine()
}

async function resume() {
  const id = sessionStorage.getItem(DRAFT_KEY)
  if (id) {
    try {
      const { data } = await http.get<Order>(`/orders/${id}`)
      if (data.status === 'DRAFT') return (order.value = data)
    } catch { /* hoa don khong con -> tao moi */ }
  }
  await startNew()
}

async function newInvoice() {
  if (order.value?.items.length && !done.value) {
    await ElMessageBox.confirm('Hóa đơn đang nhập sẽ bị hủy. Tạo hóa đơn mới?', 'Tạo hóa đơn mới', {
      confirmButtonText: 'Hủy và tạo mới', cancelButtonText: 'Ở lại', type: 'warning',
    })
    await http.delete(`/orders/${order.value.id}`).catch(() => {})
  }
  await startNew()
}

// ---------- them dong hang (ProductDialog cu) ----------
// Tim khong dau, theo tu (backend). Bo qua ket qua cu ve muon hon ket qua moi.
const productQuery = ref('')
const productLoading = ref(false)
let productSeq = 0
const fetchProducts = debounce(async (q: string, seq: number) => {
  try {
    const data = (await http.get<Product[]>('/products', { params: { q } })).data
    if (seq === productSeq) products.value = data
  } finally {
    if (seq === productSeq) productLoading.value = false
  }
}, 200)
function searchProducts(q: string) {
  productQuery.value = q
  productLoading.value = true
  fetchProducts(q, ++productSeq)
}

// Chon xong (Enter chon luon ket qua dau) -> nhay sang So luong, boi den san de go de
async function onPickProduct() {
  if (!line.productId) return
  line.unitPrice = Number(picked.value?.price ?? 0)
  await nextTick()
  qtyField.value?.focus()
}

// ---------- them nhanh san pham khi tim khong thay (chi quan tri) ----------
const newProd = reactive({ open: false, name: '', unit: '', price: 0, categoryId: null as number | null })
const newProdForm = ref<FormInstance>()
const newProdUnit = ref()
const categories = ref<Category[]>([])
async function openNewProduct() {
  const q = productQuery.value.trim()
  Object.assign(newProd, { open: true, name: q.charAt(0).toUpperCase() + q.slice(1), unit: '', price: 0, categoryId: null })
  productSelect.value?.blur()
  if (!categories.value.length) categories.value = (await http.get<Category[]>('/categories')).data
}
async function createProduct() {
  if (!(await newProdForm.value?.validate().catch(() => false))) return
  const { open, ...body } = newProd
  const { data } = await http.post<Product>('/products', body)
  products.value = [data, ...products.value]
  line.productId = data.id
  newProd.open = false
  line.unitPrice = Number(data.price)
  ElMessage.success(`Đã thêm sản phẩm "${data.name}"`)
}
// dong hop thoai: da tao -> sang So luong; huy -> quay lai o tim
function afterNewProduct() {
  if (line.productId) qtyField.value?.focus()
  else productSelect.value?.focus()
}

function resetLine() {
  Object.assign(line, { productId: null, quantity: 1, sheets: 0, factor: 0, unitPrice: 0 })
}

async function addItem() {
  if (!order.value) return
  if (!line.productId) return ElMessage.warning('Chọn sản phẩm trước')
  if (!(line.quantity > 0)) return ElMessage.warning('Số lượng phải lớn hơn 0')
  busy.value = true
  try {
    order.value = (await http.post<Order>(`/orders/${order.value.id}/items`, { ...line })).data
    resetLine()
    await nextTick()
    productSelect.value?.focus()
  } finally {
    busy.value = false
  }
}

// ---------- sua dong hang (ModifyProduct cu) ----------
const edit = reactive({ open: false, id: 0, name: '', productId: 0, quantity: 1, sheets: 0, factor: 0, unitPrice: 0 })
const editPreview = computed(() => previewLine(edit.quantity || 0, edit.sheets || 0, edit.factor || 0, edit.unitPrice || 0))
let loadingEdit = false
watch(() => edit.sheets, (v, old) => { if (!loadingEdit && edit.open && v && v !== old) edit.factor = 1 })

function openEdit(i: OrderItem) {
  loadingEdit = true
  Object.assign(edit, {
    open: true, id: i.id, name: i.productName, productId: i.productId,
    quantity: Number(i.quantity), sheets: i.sheets, factor: Number(i.factor), unitPrice: Number(i.unitPrice),
  })
  nextTick(() => (loadingEdit = false)) // sau khi watcher chay xong
}

async function saveEdit() {
  const { open, id, name, ...body } = edit
  order.value = (await http.put<Order>(`/orders/${order.value!.id}/items/${id}`, body)).data
  edit.open = false
}

async function removeItem(i: OrderItem) {
  await ElMessageBox.confirm(`Bỏ "${i.productName}" khỏi hóa đơn?`, 'Xóa dòng hàng', {
    confirmButtonText: 'Xóa', cancelButtonText: 'Không', type: 'warning',
  })
  order.value = (await http.delete<Order>(`/orders/${order.value!.id}/items/${i.id}`)).data
}

// ---------- khach hang (CustomerDialog cu) ----------
const searchCustomers = debounce(async (q: string) => {
  customers.value = (await http.get<Customer[]>('/customers', { params: { q } })).data
})

const newCus = reactive({ open: false, name: '', phone: '', address: '' })
const newCusForm = ref<FormInstance>()
async function createCustomer() {
  if (!(await newCusForm.value?.validate().catch(() => false))) return
  const { data } = await http.post<Customer>('/customers', { name: newCus.name, phone: newCus.phone, address: newCus.address })
  customers.value = [data, ...customers.value]
  customer.value = data
  Object.assign(newCus, { open: false, name: '', phone: '', address: '' })
}

// ---------- thanh toan (PayDialog cu) ----------
const itemsTotal = computed(() => Number(order.value?.itemsTotal ?? 0))
const oldDebt = computed(() => Number(customer.value?.debt ?? 0))
const payable = computed(() => itemsTotal.value + Number(pay.shippingFee || 0) + oldDebt.value)
// PayDialog cu: Con lai = max(0, Tong thanh toan - Khach tra); tra du thi la tien thua tra lai khach
const remain = computed(() => Math.max(0, payable.value - Number(pay.paidAmount || 0)))
const change = computed(() => Math.max(0, Number(pay.paidAmount || 0) - payable.value))
const needName = computed(() => !customer.value && !pay.walkInName.trim())
// ly do nut Chot bi khoa - hien ngay tren nut, khong chi lam mo nut
const blockReason = computed(() => {
  if (!order.value?.items.length) return 'Thêm ít nhất 1 mặt hàng để chốt hóa đơn.'
  if (needName.value) return 'Chọn khách hàng hoặc nhập tên khách lẻ để chốt.'
  return ''
})

// Nut "Cap nhat" no cu trong PayDialog cu: dat lai so no cua khach (chi quan tri)
async function editOldDebt() {
  const c = customer.value!
  const { value } = await ElMessageBox.prompt(`Nợ hiện tại của ${c.name}: ${money(c.debt)}. Nhập số nợ đúng:`, 'Cập nhật nợ cũ', {
    inputValue: String(Number(c.debt)), inputPattern: /^[\d.,\s]*\d[\d.,\s]*$/, inputErrorMessage: 'Nhập số tiền, ví dụ 12450000 hoặc 12.450.000',
    confirmButtonText: 'Cập nhật', cancelButtonText: 'Hủy',
  })
  const { data } = await http.post<Customer>(`/customers/${c.id}/debt-adjustments`, { debt: Number(value.replace(/\D/g, '')), note: 'Cập nhật nợ cũ khi bán hàng' })
  customer.value = data
  customers.value = customers.value.map((x) => (x.id === data.id ? data : x))
  ElMessage.success('Đã cập nhật nợ cũ')
}

function payAll() { pay.paidAmount = payable.value }
function payGoodsOnly() { pay.paidAmount = itemsTotal.value + Number(pay.shippingFee || 0) }

async function checkout() {
  if (!order.value?.items.length) return
  if (needName.value) return ElMessage.warning('Vui lòng chọn khách hàng hoặc nhập tên khách lẻ')
  busy.value = true
  try {
    const { data } = await http.post<Order>(`/orders/${order.value.id}/checkout`, {
      customerId: customer.value?.id ?? null,
      customerName: customer.value ? null : pay.walkInName.trim(),
      customerPhone: customer.value ? null : pay.walkInPhone,
      shippingFee: pay.shippingFee || 0,
      paidAmount: pay.paidAmount || 0,
      note: pay.note,
    })
    done.value = data
    sessionStorage.removeItem(DRAFT_KEY)
    ElMessage.success(`Đã chốt ${data.code}`)
  } finally {
    busy.value = false
  }
}

// In hoa don bang mau Jasper cu (Bill.jrxml) - backend tra ve PDF
function print() { openPdf(`/orders/${done.value!.id}/invoice.pdf`) }

async function init() {
  loadError.value = false
  try {
    await resume()
  } catch {
    loadError.value = true
    return
  }
  searchProducts('')
  searchCustomers('')
  await nextTick()
  productSelect.value?.focus() // vao man la go ten san pham ngay
}

onMounted(init)

// Roi man hinh khi hoa don nhap con trong thi xoa luon
onBeforeUnmount(() => {
  if (order.value && !done.value && order.value.items.length === 0) {
    http.delete(`/orders/${order.value.id}`).catch(() => {})
    sessionStorage.removeItem(DRAFT_KEY)
  }
})
</script>

<template>
  <div v-if="order" class="sale">
    <!-- cot trai: nhap hang -->
    <section class="left">
      <header class="head">
        <div>
          <h1>Bán hàng</h1>
          <p class="muted">Hóa đơn <span class="mono">{{ order.code }}</span> · {{ done ? 'đã chốt' : 'đang nhập' }}</p>
        </div>
        <el-button :icon="DocumentAdd" @click="newInvoice">Hóa đơn mới</el-button>
      </header>

      <form class="entry panel" :class="{ disabled: !!done }" @submit.prevent="addItem">
        <div class="f product">
          <label for="pick-product">Sản phẩm</label>
          <el-select id="pick-product" ref="productSelect" v-model="line.productId" filterable remote :remote-method="searchProducts"
                     default-first-option :loading="productLoading" placeholder="Gõ tên, không cần dấu (vd: ton 0,4)"
                     :disabled="!!done" popper-class="product-popper" @change="onPickProduct">
            <el-option v-for="p in products" :key="p.id" :value="p.id" :label="p.name">
              <div class="opt">
                <span class="opt-name">
                  <template v-for="(seg, i) in highlight(p.name, productQuery)" :key="i"><mark v-if="seg.m">{{ seg.t }}</mark><template v-else>{{ seg.t }}</template></template>
                  <small v-if="p.categoryName" class="muted"> · {{ p.categoryName }}</small>
                </span>
                <span class="num muted">{{ num(p.price) }} / {{ p.unit || 'đv' }}</span>
              </div>
            </el-option>
            <template #loading><div class="pick-empty muted">Đang tìm…</div></template>
            <template #empty>
              <div class="pick-empty">
                <template v-if="productLoading"><span class="muted">Đang tìm…</span></template>
                <template v-else>
                  <span>Không thấy sản phẩm <b>“{{ productQuery }}”</b>.</span>
                  <span class="muted">Thử gõ ít chữ hơn, ví dụ chỉ loại hàng và quy cách.</span>
                  <el-button v-if="auth.isAdmin && productQuery.trim()" type="primary" plain :icon="Plus" @mousedown.prevent @click="openNewProduct">
                    Thêm sản phẩm “{{ productQuery.trim() }}”
                  </el-button>
                  <span v-else-if="!auth.isAdmin" class="muted">Báo quản trị để thêm sản phẩm mới.</span>
                </template>
              </div>
            </template>
          </el-select>
        </div>
        <div class="f">
          <label for="q">Số lượng</label>
          <NumberField id="q" ref="qtyField" v-model="line.quantity" kind="decimal" :disabled="!!done" />
        </div>
        <div class="f">
          <label for="s">Số tấm</label>
          <NumberField id="s" v-model="line.sheets" kind="integer" :disabled="!!done" />
        </div>
        <div class="f">
          <label for="k">Hệ số</label>
          <NumberField id="k" v-model="line.factor" kind="decimal" :step="0.01" :precision="4" :disabled="!!done" />
        </div>
        <div class="f">
          <label for="p">Đơn giá</label>
          <NumberField id="p" v-model="line.unitPrice" kind="integer" :step="1000" :disabled="!!done" />
        </div>
        <div class="calc" aria-live="polite">
          <span class="muted">Tổng SL</span>
          <b class="num">{{ qty(preview.totalQuantity) }}</b>
          <span v-if="line.sheets" class="muted small">= {{ qty(line.quantity) }} × {{ line.sheets }} tấm × {{ qty(preview.factor) }}</span>
          <span class="sep" />
          <span class="muted">Thành tiền</span>
          <b class="num">{{ money(preview.lineTotal) }}</b>
        </div>
        <el-button type="primary" native-type="submit" :icon="Plus" :loading="busy" :disabled="!!done" class="add">Thêm vào hóa đơn</el-button>
      </form>

      <div class="panel">
        <el-table :data="order.items" empty-text="Chưa có mặt hàng. Chọn sản phẩm ở trên rồi bấm Thêm (hoặc Enter)." style="width: 100%">
          <el-table-column type="index" label="#" width="40" />
          <el-table-column label="Mặt hàng" min-width="160">
            <template #default="{ row }">{{ row.productName }}<span v-if="row.unit" class="muted"> · {{ row.unit }}</span></template>
          </el-table-column>
          <el-table-column label="SL" width="72" align="right"><template #default="{ row }"><span class="num">{{ qty(row.quantity) }}</span></template></el-table-column>
          <el-table-column label="Tổng SL" width="112" align="right">
            <template #default="{ row }">
              <span class="num">{{ qty(row.totalQuantity) }}</span>
              <div v-if="row.sheets" class="formula num" :title="`Số tấm ${row.sheets}, hệ số ${qty(row.factor)}`">× {{ row.sheets }} tấm × {{ qty(row.factor) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Đơn giá" width="100" align="right"><template #default="{ row }"><span class="num">{{ num(row.unitPrice) }}</span></template></el-table-column>
          <el-table-column label="Thành tiền" width="120" align="right"><template #default="{ row }"><b class="num">{{ num(row.lineTotal) }}</b></template></el-table-column>
          <el-table-column v-if="!done" label="Sửa / xóa" width="104" align="center">
            <template #default="{ row }">
              <div class="row-actions">
              <el-button text :icon="Edit" :aria-label="`Sửa ${row.productName}`" @click="openEdit(row)" />
              <el-button text type="danger" :icon="Delete" :aria-label="`Xóa ${row.productName}`" @click="removeItem(row)" />
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <!-- cot phai: phieu thanh toan -->
    <aside class="ticket panel" aria-label="Thanh toán">
      <div class="block">
        <div class="row-between">
          <label class="lbl" for="cus">Khách hàng</label>
          <el-button v-if="!done" link type="primary" :icon="Plus" @click="newCus.open = true">Khách mới</el-button>
        </div>
        <el-select id="cus" v-model="customer" value-key="id" filterable remote clearable :remote-method="searchCustomers"
                   placeholder="Khách lẻ (không ghi nợ)" :disabled="!!done" style="width: 100%">
          <el-option v-for="c in customers" :key="c.id" :value="c" :label="c.name">
            <div class="opt"><span>{{ c.name }} <span class="muted">{{ c.phone }}</span></span>
              <span v-if="Number(c.debt) > 0" class="num tone-debt">nợ {{ num(c.debt) }}</span></div>
          </el-option>
        </el-select>
        <p v-if="customer" class="cus-meta muted">{{ customer.phone || 'Chưa có SĐT' }}<span v-if="customer.address"> · {{ customer.address }}</span></p>
        <div v-else-if="!done" class="walkin">
          <div class="f">
            <label for="walkin-name">Tên khách lẻ <span class="req" aria-hidden="true">*</span></label>
            <el-input id="walkin-name" v-model="pay.walkInName" aria-required="true" />
          </div>
          <div class="f">
            <label for="walkin-phone">SĐT</label>
            <el-input id="walkin-phone" v-model="pay.walkInPhone" inputmode="tel" />
          </div>
        </div>
      </div>

      <dl class="sum">
        <div><dt>Tiền hàng <span class="muted">({{ order.items.length }} dòng)</span></dt><dd class="num">{{ num(itemsTotal) }}</dd></div>
        <div>
          <dt><label for="ship">Phí vận chuyển</label></dt>
          <dd class="input-dd">
            <NumberField id="ship" v-model="pay.shippingFee" kind="integer" :step="10000" :disabled="!!done" class="mini" />
            <span v-if="pay.shippingFee >= 1000" class="echo">= {{ num(pay.shippingFee) }}</span>
          </dd>
        </div>
        <div>
          <dt>Nợ cũ <el-button v-if="customer && auth.isAdmin && !done" link type="primary" size="small" @click="editOldDebt">Cập nhật</el-button></dt>
          <dd class="num" :class="{ 'tone-debt': oldDebt > 0 }">{{ num(oldDebt) }}</dd>
        </div>
        <div class="total"><dt>Tổng phải trả</dt><dd class="num">{{ num(payable) }}</dd></div>
        <div>
          <dt><label for="paid">Khách trả</label></dt>
          <dd class="input-dd">
            <NumberField id="paid" v-model="pay.paidAmount" kind="integer" :step="10000" :disabled="!!done" class="mini" />
            <span v-if="pay.paidAmount >= 1000" class="echo">= {{ num(pay.paidAmount) }}</span>
          </dd>
        </div>
        <div v-if="!done" class="quick">
          <el-button size="small" @click="payAll">Trả đủ</el-button>
          <el-button v-if="oldDebt > 0" size="small" @click="payGoodsOnly">Trả tiền hàng</el-button>
          <el-button v-if="customer" size="small" @click="pay.paidAmount = 0">Ghi nợ hết</el-button>
        </div>
        <div class="remain" :class="{ owe: remain > 0 }">
          <dt>{{ remain > 0 ? (customer ? 'Còn nợ sau đơn' : 'Còn lại') : 'Còn lại' }}</dt>
          <dd class="num">{{ num(remain) }}</dd>
        </div>
        <div v-if="change > 0"><dt>Tiền thừa trả khách</dt><dd class="num">{{ num(change) }}</dd></div>
      </dl>
      <p v-if="!customer && remain > 0" class="hint">Khách lẻ: phần còn lại chỉ ghi trên hóa đơn, không vào sổ công nợ.</p>

      <el-input v-model="pay.note" type="textarea" :rows="2" placeholder="Ghi chú (giao hàng, cắt theo kích thước...)" aria-label="Ghi chú" :disabled="!!done" />

      <template v-if="!done">
        <p v-if="blockReason" id="block-reason" class="hint warn" role="status">{{ blockReason }}</p>
        <el-button type="success" size="large" class="cta" :loading="busy" :disabled="!!blockReason"
                   :aria-describedby="blockReason ? 'block-reason' : 'cta-note'" @click="checkout">
          Chốt hóa đơn
        </el-button>
        <p v-if="!blockReason" id="cta-note" class="cta-note">
          Thu <b class="num">{{ money(pay.paidAmount || 0) }}</b>
          <template v-if="remain > 0"> · {{ customer ? 'ghi nợ' : 'còn lại' }} <b class="num tone-debt">{{ money(remain) }}</b></template>
          <template v-else-if="change > 0"> · trả lại khách <b class="num">{{ money(change) }}</b></template>
        </p>
      </template>
      <div v-else class="done">
        <p><b>Đã chốt {{ done.code }}.</b> <span v-if="Number(done.debtAfter) > 0">Khách còn nợ <b class="num tone-debt">{{ money(done.debtAfter) }}</b>.</span></p>
        <div class="done-actions">
          <el-button type="primary" size="large" :icon="Printer" @click="print">In hóa đơn</el-button>
          <el-button size="large" :icon="DocumentAdd" @click="startNew">Hóa đơn mới</el-button>
        </div>
      </div>
    </aside>

    <el-dialog v-model="edit.open" :title="`Sửa: ${edit.name}`" width="440px">
      <el-form label-position="top" class="edit-grid">
        <el-form-item label="Số lượng" for="e-q"><NumberField id="e-q" v-model="edit.quantity" kind="decimal" /></el-form-item>
        <el-form-item label="Số tấm" for="e-s"><NumberField id="e-s" v-model="edit.sheets" kind="integer" /></el-form-item>
        <el-form-item label="Hệ số" for="e-k"><NumberField id="e-k" v-model="edit.factor" kind="decimal" :step="0.01" :precision="4" /></el-form-item>
        <el-form-item label="Đơn giá" for="e-p"><NumberField id="e-p" v-model="edit.unitPrice" kind="integer" :step="1000" /></el-form-item>
      </el-form>
      <p class="muted">Tổng SL <b class="num">{{ qty(editPreview.totalQuantity) }}</b> · Thành tiền <b class="num">{{ money(editPreview.lineTotal) }}</b></p>
      <template #footer>
        <el-button @click="edit.open = false">Hủy</el-button>
        <el-button type="primary" @click="saveEdit">Lưu dòng</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="newProd.open" title="Thêm sản phẩm mới" width="440px" @opened="newProdUnit?.focus()" @closed="afterNewProduct">
      <el-form ref="newProdForm" :model="newProd" label-position="top" @submit.prevent="createProduct">
        <el-form-item label="Tên sản phẩm" prop="name" :rules="[{ required: true, message: 'Nhập tên sản phẩm', trigger: 'blur' }]">
          <el-input v-model="newProd.name" />
        </el-form-item>
        <div class="edit-grid">
          <el-form-item label="Đơn vị tính" prop="unit" :rules="[{ required: true, message: 'Nhập đơn vị (m, kg, cây...)', trigger: 'blur' }]">
            <el-input ref="newProdUnit" v-model="newProd.unit" placeholder="m, kg, cây, tấm" />
          </el-form-item>
          <el-form-item label="Giá bán (₫)" for="np-price">
            <NumberField id="np-price" v-model="newProd.price" kind="integer" :step="1000" />
            <span v-if="newProd.price >= 1000" class="echo">= {{ num(newProd.price) }}</span>
          </el-form-item>
        </div>
        <el-form-item label="Loại hàng">
          <el-select v-model="newProd.categoryId" clearable placeholder="Chưa phân loại" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newProd.open = false">Hủy</el-button>
        <el-button type="primary" @click="createProduct">Lưu và chọn</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="newCus.open" title="Thêm khách hàng" width="420px">
      <el-form ref="newCusForm" :model="newCus" label-position="top" @submit.prevent="createCustomer">
        <el-form-item label="Tên khách" prop="name" :rules="[{ required: true, message: 'Nhập tên khách', trigger: 'blur' }]">
          <el-input v-model="newCus.name" autofocus />
        </el-form-item>
        <el-form-item label="Số điện thoại"><el-input v-model="newCus.phone" inputmode="tel" /></el-form-item>
        <el-form-item label="Địa chỉ"><el-input v-model="newCus.address" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newCus.open = false">Hủy</el-button>
        <el-button type="primary" @click="createCustomer">Lưu và chọn</el-button>
      </template>
    </el-dialog>

  </div>
  <div v-else-if="loadError" class="state panel panel-pad" role="alert">
    <h1>Không mở được hóa đơn</h1>
    <p class="muted">Kiểm tra kết nối tới máy chủ rồi thử lại. Hóa đơn đang nhập dở (nếu có) vẫn được giữ.</p>
    <el-button type="primary" @click="init">Thử lại</el-button>
  </div>
  <div v-else class="state panel panel-pad" aria-busy="true">
    <el-skeleton :rows="6" animated />
  </div>
</template>

<style scoped>
.sale { display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: var(--space-xl); align-items: start; }
.left { display: flex; flex-direction: column; gap: var(--space-lg); min-width: 0; }
.head { display: flex; justify-content: space-between; align-items: flex-end; gap: var(--space-lg); flex-wrap: wrap; }
.head h1 { font-size: 22px; }
.head p { margin: 2px 0 0; font-size: 14px; }

.entry { display: grid; grid-template-columns: minmax(160px, 2fr) repeat(4, minmax(100px, 1fr)); gap: var(--space-md) var(--space-lg); padding: var(--space-lg) var(--space-xl); align-items: end; }
.entry.disabled { opacity: 0.6; }
.f { display: flex; flex-direction: column; gap: var(--space-sm); min-width: 0; }
.f label, .lbl { font-size: 13px; font-weight: 500; color: #334155; }
.f :deep(.el-select) { width: 100%; }
.calc { grid-column: 1 / -3; display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--space-sm) var(--space-md); font-size: 14px; padding-top: var(--space-sm); }
.calc b { font-size: 16px; }
.calc .small { font-size: 12px; }
.calc .sep { width: 1px; height: 14px; background: var(--steel-200); margin: 0 var(--space-sm); }
.add { grid-column: -3 / -1; }
.opt { display: flex; justify-content: space-between; gap: var(--space-lg); }
.opt-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
.opt-name mark { background: #FEF3C7; color: inherit; font-weight: 600; border-radius: 2px; padding: 0 1px; }
.pick-empty { display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-sm); padding: var(--space-md) var(--space-lg); font-size: 14px; }

/* phieu: diem nhan duy nhat cua man - nhu to hoa don xe rang cua */
.ticket { position: sticky; top: var(--space-xl); padding: var(--space-xl); display: flex; flex-direction: column; gap: var(--space-lg); border-top: 4px solid var(--steel-800); }
.block { display: flex; flex-direction: column; gap: var(--space-sm); }
.row-between { display: flex; justify-content: space-between; align-items: center; }
.cus-meta { margin: 0; font-size: 13px; }
.walkin { display: grid; grid-template-columns: 1fr 128px; gap: var(--space-sm); }
.req { color: var(--danger); }
.row-actions { display: flex; justify-content: center; gap: var(--space-xs); }
.row-actions .el-button { width: 36px; height: 36px; margin: 0; }
.formula { font-size: 11px; color: var(--ink-muted); line-height: 1.3; white-space: nowrap; }
.input-dd { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; padding: 2px 0; }
.sum { margin: 0; display: flex; flex-direction: column; border-top: 1px dashed var(--steel-200); padding-top: var(--space-md); }
.sum > div { display: flex; justify-content: space-between; align-items: center; gap: var(--space-md); min-height: 34px; }
.sum dt { font-size: 14px; color: #334155; }
.sum dd { margin: 0; font-size: 15px; }
.mini { width: 140px; }

.total { border-top: 1px solid var(--steel-200); border-bottom: 1px solid var(--steel-200); margin: var(--space-sm) 0; padding: var(--space-sm) 0; }
.total dt { font-weight: 600; color: var(--ink); }
.total dd { font-size: 22px; font-weight: 600; }
.quick { justify-content: flex-end !important; gap: var(--space-sm) !important; min-height: 0 !important; padding-bottom: var(--space-sm); }
.quick .el-button + .el-button { margin-left: 0; }
.remain dd { font-size: 18px; font-weight: 600; }
.remain.owe dt, .remain.owe dd { color: var(--debt); }
.hint { margin: 0; color: var(--ink-muted); font-size: 13px; }
.hint.warn { color: var(--debt); font-weight: 500; }
.cta-note { margin: calc(-1 * var(--space-sm)) 0 0; font-size: 13px; color: var(--ink-muted); text-align: center; }
.state { max-width: 560px; display: flex; flex-direction: column; gap: var(--space-md); align-items: flex-start; }
.state h1 { font-size: 20px; }
.state p { margin: 0; }
.cta { width: 100%; height: 52px; font-size: 16px; }
.done p { margin: 0 0 var(--space-md); }
.done-actions { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-sm); }
.done-actions .el-button + .el-button { margin-left: 0; }
.edit-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 var(--space-lg); }


@media (max-width: 1180px) {
  .sale { grid-template-columns: 1fr; }
  .ticket { position: static; }
}
@media (max-width: 720px) {
  .entry { grid-template-columns: 1fr 1fr; }
  .f.product, .calc, .add { grid-column: 1 / -1; }
}
</style>
