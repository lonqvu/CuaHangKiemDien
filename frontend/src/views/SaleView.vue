<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Plus, Edit, Delete, Printer, DocumentAdd } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Customer, Order, OrderItem, Page, Product } from '../api/types'
import { debounce, money, num, previewLine, qty } from '../format'
import { openPdf } from '../api/pdf'
import { useAuth } from '../stores/auth'

const auth = useAuth()
const route = useRoute()
const router = useRouter()

const DRAFT_KEY = 'sale.draftId'

const order = ref<Order | null>(null)
const done = ref<Order | null>(null) // hoa don vua chot, de in
const products = ref<Product[]>([])
const customers = ref<Customer[]>([])
const customer = ref<Customer | null>(null)
const busy = ref(false)
const productSelect = ref()
const customerSelect = ref()

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
  const fromLink = route.query.draft ? String(route.query.draft) : null
  if (fromLink) router.replace({ query: {} })
  const id = fromLink ?? sessionStorage.getItem(DRAFT_KEY)
  if (id) {
    try {
      const { data } = await http.get<Order>(`/orders/${id}`)
      if (data.status === 'DRAFT') {
        sessionStorage.setItem(DRAFT_KEY, String(data.id))
        return (order.value = data)
      }
      if (fromLink) ElMessage.info('Hóa đơn này đã chốt hoặc đã xóa, mở hóa đơn mới.')
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
const searchProducts = debounce(async (q: string) => {
  products.value = (await http.get<Page<Product>>('/products', { params: { q, size: 30 } })).data.items
})

function onPickProduct() {
  line.unitPrice = Number(picked.value?.price ?? 0)
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
  customers.value = (await http.get<Page<Customer>>('/customers', { params: { q, size: 20 } })).data.items
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

// Nut "Cap nhat" no cu trong PayDialog cu: dat lai so no cua khach (chi quan tri)
async function editOldDebt() {
  const c = customer.value!
  const { value } = await ElMessageBox.prompt(`Nợ hiện tại của ${c.name}: ${money(c.debt)}. Nhập số nợ đúng:`, 'Cập nhật nợ cũ', {
    inputValue: String(Number(c.debt)), inputPattern: /^\d+$/, inputErrorMessage: 'Nhập số tiền (chỉ chữ số)',
    confirmButtonText: 'Cập nhật', cancelButtonText: 'Hủy',
  })
  const { data } = await http.post<Customer>(`/customers/${c.id}/debt-adjustments`, { debt: Number(value), note: 'Cập nhật nợ cũ khi bán hàng' })
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

// Phim tat o quay: F2 chon san pham, F4 chon khach, F9 chot hoa don
function onKey(e: KeyboardEvent) {
  if (e.key === 'F2') { e.preventDefault(); if (!done.value) productSelect.value?.focus() }
  else if (e.key === 'F4') { e.preventDefault(); if (!done.value) customerSelect.value?.focus() }
  else if (e.key === 'F9') {
    e.preventDefault()
    if (!done.value && order.value?.items.length && !needName.value && !busy.value) checkout()
    else if (needName.value && order.value?.items.length) ElMessage.warning('Chọn khách hàng (F4) hoặc nhập tên khách lẻ trước khi chốt')
  }
}

// Dong tab / tai lai khi hoa don nhap con trong -> xoa luon (keepalive de request van gui khi trang dong)
function dropEmptyDraft() {
  if (order.value && !done.value && order.value.items.length === 0) {
    fetch(`/api/orders/${order.value.id}`, { method: 'DELETE', keepalive: true, headers: { Authorization: `Bearer ${localStorage.getItem('token')}` } }).catch(() => {})
    sessionStorage.removeItem(DRAFT_KEY)
  }
}

const ticketEl = ref<HTMLElement>()
function goToTicket() { ticketEl.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }

onMounted(async () => {
  window.addEventListener('keydown', onKey)
  window.addEventListener('pagehide', dropEmptyDraft)
  await resume()
  searchProducts('')
  searchCustomers('')
})

// Roi man hinh khi hoa don nhap con trong thi xoa luon
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  window.removeEventListener('pagehide', dropEmptyDraft)
  dropEmptyDraft()
})
</script>

<template>
  <div v-if="order" class="sale">
    <!-- cot trai: nhap hang -->
    <section class="left">
      <header class="head">
        <div>
          <h1>Bán hàng</h1>
          <p class="muted">
            <template v-if="done">Đã chốt hóa đơn <span class="mono">{{ done.code }}</span></template>
            <template v-else>Hóa đơn mới, đang nhập. Mã hóa đơn được cấp khi chốt.</template>
          </p>
        </div>
        <el-button :icon="DocumentAdd" @click="newInvoice">Hóa đơn mới</el-button>
      </header>

      <form class="entry panel" :class="{ disabled: !!done }" @submit.prevent="addItem">
        <div class="f product">
          <label for="pick-product">Sản phẩm <kbd>F2</kbd></label>
          <el-select id="pick-product" ref="productSelect" v-model="line.productId" filterable remote :remote-method="searchProducts"
                     placeholder="Gõ tên để tìm" :disabled="!!done" @change="onPickProduct">
            <el-option v-for="p in products" :key="p.id" :value="p.id" :label="p.name">
              <div class="opt"><span>{{ p.name }}</span>
                <span class="num muted">{{ num(p.price) }}/{{ p.unit || 'đv' }} · <span :class="{ 'tone-danger': p.onHand != null && Number(p.onHand) <= 0 }">tồn {{ p.onHand == null ? '—' : qty(p.onHand) }}</span></span></div>
            </el-option>
          </el-select>
        </div>
        <div class="f">
          <label for="q">Số lượng</label>
          <el-input-number id="q" v-model="line.quantity" :min="0" :step="1" controls-position="right" :disabled="!!done" />
        </div>
        <div class="f">
          <label for="s">Số tấm</label>
          <el-input-number id="s" v-model="line.sheets" :min="0" :step="1" controls-position="right" :disabled="!!done" />
        </div>
        <div class="f">
          <label for="k">Hệ số</label>
          <el-input-number id="k" v-model="line.factor" :min="0" :step="0.01" controls-position="right"
                           :placeholder="line.sheets ? '1,08' : '0'" :disabled="!!done" />
        </div>
        <div class="f">
          <label for="p">Đơn giá</label>
          <el-input-number id="p" v-model="line.unitPrice" :min="0" :step="1000" controls-position="right" :disabled="!!done" />
        </div>
        <div class="calc" aria-live="polite">
          <span class="muted">Tổng SL</span>
          <b class="num">{{ qty(preview.totalQuantity) }}</b>
          <span v-if="line.sheets" class="muted small">= {{ qty(line.quantity) }} × {{ line.sheets }} tấm × {{ qty(preview.factor) }}</span>
          <span class="sep" />
          <span class="muted">Thành tiền</span>
          <b class="num">{{ money(preview.lineTotal) }}</b>
          <span v-if="picked && picked.onHand != null" class="stockinfo" :class="{ over: preview.totalQuantity > Number(picked.onHand) }">
            {{ preview.totalQuantity > Number(picked.onHand) ? 'Vượt tồn kho' : 'Tồn' }}: <b class="num">{{ qty(picked.onHand) }}</b> {{ picked.unit }}
          </span>
        </div>
        <el-button type="primary" native-type="submit" :icon="Plus" :loading="busy" :disabled="!!done" class="add">Thêm vào hóa đơn</el-button>
      </form>

      <div class="panel">
        <el-table :data="order.items" empty-text="Chưa có mặt hàng. Chọn sản phẩm ở trên (F2) rồi bấm Thêm hoặc Enter." style="width: 100%">
          <el-table-column type="index" label="#" width="40" />
          <el-table-column label="Mặt hàng" min-width="150">
            <template #default="{ row }">{{ row.productName }}</template>
          </el-table-column>
          <el-table-column label="SL × tấm × hệ số" width="150" align="right">
            <template #default="{ row }">
              <span class="num">{{ qty(row.quantity) }}<template v-if="row.sheets"> × {{ row.sheets }} × {{ qty(row.factor) }}</template></span>
            </template>
          </el-table-column>
          <el-table-column label="Tổng SL" width="96" align="right">
            <template #default="{ row }"><span class="num">{{ qty(row.totalQuantity) }}</span> <span class="muted small">{{ row.unit }}</span></template>
          </el-table-column>
          <el-table-column label="Đơn giá" width="100" align="right"><template #default="{ row }"><span class="num">{{ num(row.unitPrice) }}</span></template></el-table-column>
          <el-table-column label="Thành tiền" width="116" align="right"><template #default="{ row }"><b class="num">{{ num(row.lineTotal) }}</b></template></el-table-column>
          <el-table-column v-if="!done" width="84" align="center">
            <template #default="{ row }">
              <el-button text :icon="Edit" :aria-label="`Sửa ${row.productName}`" @click="openEdit(row)" />
              <el-button text type="danger" :icon="Delete" :aria-label="`Xóa ${row.productName}`" @click="removeItem(row)" />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <!-- cot phai: phieu thanh toan -->
    <aside ref="ticketEl" class="ticket panel" aria-label="Thanh toán">
      <div class="block">
        <div class="row-between">
          <label class="lbl" for="cus">Khách hàng <kbd>F4</kbd></label>
          <el-button v-if="!done" link type="primary" @click="newCus.open = true">+ Khách mới</el-button>
        </div>
        <el-select id="cus" ref="customerSelect" v-model="customer" value-key="id" filterable remote clearable :remote-method="searchCustomers"
                   placeholder="Khách lẻ (không ghi nợ)" :disabled="!!done" style="width: 100%">
          <el-option v-for="c in customers" :key="c.id" :value="c" :label="c.name">
            <div class="opt"><span>{{ c.name }} <span class="muted">{{ c.phone }}</span></span>
              <span v-if="Number(c.debt) > 0" class="num tone-debt">nợ {{ num(c.debt) }}</span></div>
          </el-option>
        </el-select>
        <p v-if="customer" class="cus-meta muted">{{ customer.phone || 'Chưa có SĐT' }}<span v-if="customer.address"> · {{ customer.address }}</span></p>
        <div v-else-if="!done" class="walkin">
          <el-input v-model="pay.walkInName" placeholder="Tên khách lẻ *" aria-label="Tên khách lẻ (bắt buộc)" />
          <el-input v-model="pay.walkInPhone" placeholder="SĐT" aria-label="Số điện thoại khách lẻ" />
        </div>
      </div>

      <dl class="sum">
        <div><dt>Tiền hàng <span class="muted">({{ order.items.length }} dòng)</span></dt><dd class="num">{{ num(itemsTotal) }}</dd></div>
        <div>
          <dt><label for="ship">Phí vận chuyển</label></dt>
          <dd><el-input-number id="ship" v-model="pay.shippingFee" :min="0" :step="10000" :controls="false" :disabled="!!done" class="mini" /></dd>
        </div>
        <div>
          <dt>Nợ cũ <el-button v-if="customer && auth.isAdmin && !done" link type="primary" size="small" @click="editOldDebt">Cập nhật</el-button></dt>
          <dd class="num" :class="{ 'tone-debt': oldDebt > 0 }">{{ num(oldDebt) }}</dd>
        </div>
        <div class="total"><dt>Tổng phải trả</dt><dd class="num">{{ num(payable) }}</dd></div>
        <div>
          <dt><label for="paid">Khách trả</label></dt>
          <dd><el-input-number id="paid" v-model="pay.paidAmount" :min="0" :step="10000" :controls="false" :disabled="!!done" class="mini" /></dd>
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
        <el-button type="success" size="large" class="cta" :loading="busy" :disabled="!order.items.length || needName" @click="checkout">
          Chốt hóa đơn · thu {{ money(pay.paidAmount || 0) }} <kbd class="on-dark">F9</kbd>
        </el-button>
        <p v-if="needName && order.items.length" class="hint">Chọn khách hàng hoặc nhập tên khách lẻ để chốt.</p>
      </template>
      <div v-else class="done">
        <p><b>Đã chốt {{ done.code }}.</b> <span v-if="Number(done.debtAfter) > 0">Khách còn nợ <b class="num tone-debt">{{ money(done.debtAfter) }}</b>.</span></p>
        <div class="done-actions">
          <el-button type="primary" size="large" :icon="Printer" @click="print">In hóa đơn</el-button>
          <el-button size="large" :icon="DocumentAdd" @click="startNew">Hóa đơn mới</el-button>
        </div>
      </div>
    </aside>

    <div v-if="!done" class="paybar">
      <div><span class="muted">Tổng phải trả</span> <b class="num">{{ money(payable) }}</b></div>
      <el-button type="success" :disabled="!order.items.length" @click="goToTicket">Thanh toán</el-button>
    </div>

    <el-dialog v-model="edit.open" :title="`Sửa: ${edit.name}`" width="440px">
      <el-form label-position="top" class="edit-grid">
        <el-form-item label="Số lượng"><el-input-number v-model="edit.quantity" :min="0" controls-position="right" /></el-form-item>
        <el-form-item label="Số tấm"><el-input-number v-model="edit.sheets" :min="0" controls-position="right" /></el-form-item>
        <el-form-item label="Hệ số"><el-input-number v-model="edit.factor" :min="0" :step="0.01" controls-position="right" /></el-form-item>
        <el-form-item label="Đơn giá"><el-input-number v-model="edit.unitPrice" :min="0" :step="1000" controls-position="right" /></el-form-item>
      </el-form>
      <p class="muted">Tổng SL <b class="num">{{ qty(editPreview.totalQuantity) }}</b> · Thành tiền <b class="num">{{ money(editPreview.lineTotal) }}</b></p>
      <template #footer>
        <el-button @click="edit.open = false">Hủy</el-button>
        <el-button type="primary" @click="saveEdit">Lưu dòng</el-button>
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
</template>

<style scoped>
.sale { display: grid; grid-template-columns: minmax(0, 1fr) clamp(300px, 30vw, 360px); gap: var(--space-xl); align-items: start; }
/* cot trai tu co gian theo be rong that cua no (container query), khong theo man hinh */
.left { display: flex; flex-direction: column; gap: var(--space-lg); min-width: 0; container-type: inline-size; }
.head { display: flex; justify-content: space-between; align-items: flex-end; gap: var(--space-lg); flex-wrap: wrap; }
.head h1 { font-size: 22px; }
.head p { margin: 2px 0 0; font-size: 14px; }

.entry { display: grid; grid-template-columns: minmax(200px, 2fr) repeat(4, minmax(100px, 1fr)); gap: var(--space-md) var(--space-lg); padding: var(--space-lg) var(--space-xl); align-items: end; }
@container (max-width: 700px) {
  .entry { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .f.product { grid-column: 1 / -1; }
  .calc { grid-column: 1 / -1 !important; }
  .add { grid-column: 1 / -1 !important; }
}
@container (max-width: 420px) { .entry { grid-template-columns: 1fr 1fr; } }
kbd { font-family: var(--font-mono); font-size: 11px; font-weight: 500; padding: 0 5px; border: 1px solid var(--steel-200); border-radius: 4px; color: var(--ink-muted); background: var(--steel-50); margin-left: 4px; }
kbd.on-dark { color: inherit; background: rgba(255, 255, 255, 0.15); border-color: rgba(255, 255, 255, 0.35); }
.paybar { display: none; }
.entry.disabled { opacity: 0.6; }
.f { display: flex; flex-direction: column; gap: var(--space-sm); min-width: 0; }
.f label, .lbl { font-size: 13px; font-weight: 500; color: #334155; }
.f :deep(.el-input-number), .f :deep(.el-select) { width: 100%; }
.f :deep(.el-input-number input) { text-align: right; font-family: var(--font-mono); }
.calc { grid-column: 1 / -3; display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--space-sm) var(--space-md); font-size: 14px; padding-top: var(--space-sm); }
.calc b { font-size: 16px; }
.calc .small { font-size: 12px; }
.calc .sep { width: 1px; height: 14px; background: var(--steel-200); margin: 0 var(--space-sm); }
.add { grid-column: -3 / -1; }
.small { font-size: 12px; }
.stockinfo { font-size: 13px; color: var(--ink-muted); margin-left: auto; }
.stockinfo.over { color: var(--debt); font-weight: 600; }
.opt { display: flex; justify-content: space-between; gap: var(--space-lg); }

/* phieu: diem nhan duy nhat cua man - nhu to hoa don xe rang cua */
.ticket { position: sticky; top: var(--space-xl); padding: var(--space-xl); display: flex; flex-direction: column; gap: var(--space-lg); border-top: 4px solid var(--steel-800); }
.block { display: flex; flex-direction: column; gap: var(--space-sm); }
.row-between { display: flex; justify-content: space-between; align-items: center; }
.cus-meta { margin: 0; font-size: 13px; }
.walkin { display: grid; grid-template-columns: 1fr 120px; gap: var(--space-sm); }
.sum { margin: 0; display: flex; flex-direction: column; border-top: 1px dashed var(--steel-200); padding-top: var(--space-md); }
.sum > div { display: flex; justify-content: space-between; align-items: center; gap: var(--space-md); min-height: 34px; }
.sum dt { font-size: 14px; color: #334155; }
.sum dd { margin: 0; font-size: 15px; }
.mini { width: 140px; }
.mini :deep(input) { text-align: right; font-family: var(--font-mono); }
.total { border-top: 1px solid var(--steel-200); border-bottom: 1px solid var(--steel-200); margin: var(--space-sm) 0; padding: var(--space-sm) 0; }
.total dt { font-weight: 600; color: var(--ink); }
.total dd { font-size: 22px; font-weight: 600; }
.quick { justify-content: flex-end !important; gap: var(--space-sm) !important; min-height: 0 !important; padding-bottom: var(--space-sm); }
.quick .el-button + .el-button { margin-left: 0; }
.remain dd { font-size: 18px; font-weight: 600; }
.remain.owe dt, .remain.owe dd { color: var(--debt); }
.hint { margin: 0; color: var(--ink-muted); font-size: 13px; }
.cta { width: 100%; height: 52px; font-size: 16px; }
.done p { margin: 0 0 var(--space-md); }
.done-actions { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-sm); }
.done-actions .el-button + .el-button { margin-left: 0; }
.edit-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 var(--space-lg); }
.edit-grid :deep(.el-input-number) { width: 100%; }

/* Duoi 900px: 1 cot, phieu thanh toan xuong duoi + thanh "Tong phai tra" dinh day man de khong phai cuon tim nut */
@media (max-width: 900px) {
  .sale { grid-template-columns: 1fr; padding-bottom: 72px; }
  .ticket { position: static; scroll-margin-top: 64px; }
  .paybar {
    display: flex; position: fixed; left: 0; right: 0; bottom: 0; z-index: 15; align-items: center; justify-content: space-between;
    gap: var(--space-lg); padding: var(--space-md) var(--space-xl); background: var(--surface); border-top: 1px solid var(--steel-200);
    box-shadow: 0 -4px 12px rgba(15, 23, 42, 0.08);
  }
  .paybar b { font-size: 18px; margin-left: var(--space-sm); }
  .paybar .el-button { min-height: 44px; }
}
</style>
