<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Plus, Edit, Delete, Printer, DocumentAdd, ChatDotRound } from '@element-plus/icons-vue'
import http from '../api/http'
import type { Category, Customer, Order, OrderItem, Page, Product } from '../api/types'
import { debounce, groupItems, highlight, money, num, orderMessage, parseSegments, plain, previewLine, qty } from '../format'
import ZaloSendDialog from '../components/ZaloSendDialog.vue'
import NumberField from '../components/NumberField.vue'
import DebtAdjustDialog from '../components/DebtAdjustDialog.vue'
import { openPdf } from '../api/pdf'
import { useAuth } from '../stores/auth'
import { useRemotePick } from '../composables/useRemotePick'
import { useNarrow } from '../composables/useNarrow'

const auth = useAuth()
const narrow = useNarrow() // dien thoai: dong hang dang the (ten, SL x tam, thanh tien), khong cat cot
const route = useRoute()
const router = useRouter()

const DRAFT_KEY = 'sale.draftId'

const order = ref<Order | null>(null)
const done = ref<Order | null>(null) // hoa don vua chot, de in
const customers = ref<Customer[]>([])
const customer = ref<Customer | null>(null)
const busy = ref(false)
const loadError = ref(false)
const productSelect = ref()
const customerSelect = ref()
const ctaButton = ref()
const qtyField = ref()
const segField = ref()

const line = reactive({ productId: null as number | null, quantity: 1, sheets: 0, factor: 0, unitPrice: 0 })
const pay = reactive({ shippingFee: 0, paidAmount: 0, note: '', walkInName: '', walkInPhone: '' })

const picked = computed<Product | null>(() => products.value.find((p: Product) => p.id === line.productId) ?? null)

// App cu: go so tam khac 0 thi he so tu dien 1.08 (ProductDialog); man sua dong (ModifyProduct) dien 1
// Xoa so tam ve 0 thi he so cung ve 0: neu khong, cong thuc cu (so tam 0, he so != 0) cho Tong SL = 0 -> dong 0 d
watch(() => line.sheets, (v, old) => {
  if (v && v !== old) line.factor = 1.08
  else if (!v && old) line.factor = 0
})
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
// Tim khong dau, theo tu (backend); Enter chon ket qua dau (ke ca go nhanh truoc khi ket qua ve) - xem useRemotePick.
// Da chon khach: hang khach hay mua len dau; o tim trong: hang ban chay. Giu san pham dang chon trong danh sach.
const { options: products, loading: productLoading, query: productQuery, search: searchProducts, onEnter: onProductEnter } = useRemotePick<Product>(
  async (q) => (await http.get<Page<Product>>('/products', { params: { q, size: 30, popular: true, customerId: customer.value?.id } })).data.items,
  async (first) => {
    line.productId = first.id
    productSelect.value?.blur()
    await onPickProduct()
  },
  { keep: (): Product | null => picked.value },
)
// Doi khach -> xep lai goi y theo hang khach do hay mua
watch(() => customer.value?.id, () => searchProducts(productQuery.value))

// Chon xong (Enter chon luon ket qua dau) -> nhay sang So luong, boi den san de go de
async function onPickProduct() {
  if (!line.productId) return
  // Mat hang da co trong hoa don nay: lay gia cua dong gan nhat (giong khi giu san pham nhap doan tiep) -
  // tim lai hay giu san pham deu ra cung 1 gia; khong co thi lay gia niem yet
  const last = [...(order.value?.items ?? [])].reverse().find((i) => i.productId === line.productId)
  line.unitPrice = Number(last?.unitPrice ?? picked.value?.price ?? 0)
  justAdded = false
  await nextTick()
  focusEntry()
}
// Don gia dang khac gia niem yet (da sua o dong truoc / o nay) -> ghi chu canh o Don gia, bam de ve gia niem yet
const listPrice = computed(() => (picked.value ? Number(picked.value.price) : null))
const priceEdited = computed(() => listPrice.value != null && !!line.productId && Number(line.unitPrice) !== listPrice.value)
// hang ban theo met (ton, up noc, mang...): con tro vao o "Nhieu doan"; con lai: o So luong
const isSheet = computed(() => (picked.value?.unit ?? '').trim().toLowerCase() === 'm')
function focusEntry() {
  if (isSheet.value) segField.value?.focus()
  else qtyField.value?.focus()
}

// ---------- nhap nhieu doan 1 lan: "4,7x2 4,5x2 3" ----------
const segText = ref('')
const segParsed = computed(() => parseSegments(segText.value))
const segFactor = computed(() => line.factor || 1.08) // he so dang nhap (neu co), mac dinh 1,08 nhu khi go so tam
const segPreview = computed(() => {
  const rows = segParsed.value.segments.map((g) => previewLine(g.quantity, g.sheets, g.sheets ? segFactor.value : 0, line.unitPrice || 0))
  return {
    sheets: segParsed.value.segments.reduce((a, g) => a + g.sheets, 0),
    totalQuantity: rows.reduce((a, r) => a + r.totalQuantity, 0),
    lineTotal: rows.reduce((a, r) => a + r.lineTotal, 0),
  }
})
async function addSegments() {
  if (!order.value || busy.value || !line.productId) return
  // de trong + Enter: sang nhap tung dong (ke ca ngay sau khi them - chi chuyen o, khong canh bao gi)
  if (!segText.value.trim()) return qtyField.value?.focus()
  if (segParsed.value.error) return ElMessage.warning(segParsed.value.error)
  if (!(line.unitPrice > 0)) {
    const ok = await ElMessageBox.confirm(`Đơn giá đang là 0 ₫, các đoạn này sẽ thành 0 ₫. Vẫn thêm?`, 'Đơn giá 0 ₫', {
      confirmButtonText: 'Vẫn thêm', cancelButtonText: 'Sửa đơn giá', type: 'warning',
    }).then(() => true, () => false)
    if (!ok) return document.getElementById('p')?.focus()
  }
  busy.value = true
  try {
    const body = segParsed.value.segments.map((g) => ({ productId: line.productId, quantity: g.quantity, sheets: g.sheets, factor: g.sheets ? segFactor.value : 0, unitPrice: line.unitPrice }))
    order.value = (await http.post<Order>(`/orders/${order.value.id}/items/batch`, body)).data
    segText.value = ''
    Object.assign(line, { quantity: 0, sheets: 0, factor: 0 })
    await nextTick()
    segField.value?.focus()
  } finally {
    busy.value = false
  }
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
  if (line.productId) focusEntry()
  else productSelect.value?.focus()
}

function resetLine() {
  Object.assign(line, { productId: null, quantity: 1, sheets: 0, factor: 0, unitPrice: 0 })
  segText.value = ''
}

// Ly do khong them duoc dong dang nhap (hien ngay duoi o nhap, khong cho bam Them)
const lineProblem = computed(() => {
  if (!line.productId || !(line.quantity > 0)) return '' // chua nhap SL: khong bao do, bam Them se nhac
  if (preview.value.totalQuantity === 0)
    return line.sheets ? 'Tổng SL bằng 0, kiểm tra lại hệ số.' : 'Số tấm đang là 0 nhưng hệ số khác 0 nên Tổng SL = 0. Xóa hệ số hoặc nhập số tấm.'
  return ''
})

// Vua them dong xong ma bam Enter nua (o trong): bo qua lang le, khong hien canh bao. Go gi vao o nhap thi het.
let justAdded = false
function onEntryOut() { justAdded = false }
watch(() => [line.quantity, line.sheets, line.factor, line.unitPrice, segText.value], () => { if (!busy.value) justAdded = false })

async function addItem() {
  if (!order.value || busy.value) return // chan bam Enter 2 lan khi dang gui
  if (!line.productId) return ElMessage.warning('Chọn sản phẩm trước')
  if (!(line.quantity > 0)) {
    qtyField.value?.focus()
    if (justAdded) { justAdded = false; return }
    return ElMessage.warning('Nhập số lượng lớn hơn 0')
  }
  if (lineProblem.value) return ElMessage.warning(lineProblem.value)
  if (!(line.unitPrice > 0)) {
    const ok = await ElMessageBox.confirm(`Đơn giá đang là 0 ₫, dòng này sẽ thành 0 ₫. Vẫn thêm?`, 'Đơn giá 0 ₫', {
      confirmButtonText: 'Vẫn thêm', cancelButtonText: 'Sửa đơn giá', type: 'warning',
    }).then(() => true, () => false)
    if (!ok) return document.getElementById('p')?.focus()
  }
  busy.value = true
  try {
    order.value = (await http.post<Order>(`/orders/${order.value.id}/items`, { ...line })).data
    // Giu san pham + don gia de nhap tiep doan khac cung mat hang (ton: 4,7 m, 4,5 m, 3 m...).
    // So luong ve 0 de lo bam Enter them lan nua khong tao dong trung.
    Object.assign(line, { quantity: 0, sheets: 0, factor: 0 })
    await nextTick()
    qtyField.value?.focus()
    justAdded = true // sau focus: roi khung nhap (bam xoa, sang o khac...) thi het
  } finally {
    busy.value = false
  }
}

// Esc o khung nhap: bo san pham dang giu, quay ve o tim
function clearLine() {
  resetLine()
  nextTick(() => productSelect.value?.focus())
}

// Phim tat: F2 tim san pham, F4 chon khach, F9 toi nut Chot (bam Enter de chot - 2 buoc cho chac)
function onKey(e: KeyboardEvent) {
  if (done.value || edit.open || newCus.open || newProd.open) return
  if (e.key === 'F2') { e.preventDefault(); productSelect.value?.focus() }
  else if (e.key === 'F4') { e.preventDefault(); customerSelect.value?.focus() }
  else if (e.key === 'F9') {
    e.preventDefault()
    if (blockReason.value) ElMessage.warning(blockReason.value)
    else ctaButton.value?.ref?.focus()
  }
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

// ---------- sua dong hang (ModifyProduct cu) ----------
const edit = reactive({ open: false, id: 0, name: '', productId: 0, quantity: 1, sheets: 0, factor: 0, unitPrice: 0 })
const editPreview = computed(() => previewLine(edit.quantity || 0, edit.sheets || 0, edit.factor || 0, edit.unitPrice || 0))
let loadingEdit = false
watch(() => edit.sheets, (v, old) => {
  if (loadingEdit || !edit.open) return
  if (v && v !== old) edit.factor = 1
  else if (!v && old) edit.factor = 0
})

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

// ---------- bang dong hang: gom cac doan cung san pham + don gia thanh 1 cum ----------
// Cum co tong so tam / tong met / tong tien, mo ra de sua / xoa tung doan. Mat hang 1 dong thi hien nhu cu.
const segLabel = (i: OrderItem) => (i.sheets ? `${plain(i.quantity).replace('.', ',')}×${i.sheets}` : plain(i.quantity).replace('.', ','))
const tableRows = computed(() => groupItems(order.value?.items ?? []).map((g, n) => g.items.length === 1
  ? { ...g.first, rowKey: `i${g.first.id}`, no: n + 1, isGroup: false as const }
  : {
      rowKey: `g${g.key}`, no: n + 1, isGroup: true as const, productName: g.first.productName, unit: g.first.unit, unitPrice: g.first.unitPrice,
      count: g.items.length, sheets: g.sheets, totalQuantity: g.totalQuantity, lineTotal: g.lineTotal, items: g.items,
      segs: g.items.map(segLabel).join(' + '),
      children: g.items.map((it, k) => ({ ...it, rowKey: `i${it.id}`, no: null, isGroup: false, seg: k + 1 })),
    }))

// the tren dien thoai: cum nao dang mo
const openGroups = ref(new Set<string>())
function toggleGroup(key: string) {
  const s = new Set(openGroups.value)
  if (s.has(key)) s.delete(key)
  else s.add(key)
  openGroups.value = s
}
const qtyText = (i: { quantity: number | string; sheets: number; factor: number | string }) =>
  i.sheets ? `${qty(i.quantity)} × ${i.sheets} tấm × ${qty(i.factor)}` : qty(i.quantity)

const rowClass = ({ row }: { row: { isGroup?: boolean; seg?: number } }) => (row.isGroup ? 'group-row' : row.seg ? 'seg-row' : '')

async function removeGroup(row: { productName: string; count: number; items: OrderItem[] }) {
  await ElMessageBox.confirm(`Bỏ cả ${row.count} đoạn "${row.productName}" khỏi hóa đơn?`, 'Xóa cả cụm', {
    confirmButtonText: 'Xóa cả cụm', cancelButtonText: 'Không', type: 'warning',
  })
  for (const it of row.items) order.value = (await http.delete<Order>(`/orders/${order.value!.id}/items/${it.id}`)).data
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
// ly do nut Chot bi khoa - hien ngay tren nut, khong chi lam mo nut
const blockReason = computed(() => {
  if (!order.value?.items.length) return 'Thêm ít nhất 1 mặt hàng để chốt hóa đơn.'
  if (needName.value) return 'Chọn khách hàng hoặc nhập tên khách lẻ để chốt.'
  // khach le phai tra du (khong ghi no): muon ghi no thi chon / tao khach
  if (!customer.value && remain.value > 0)
    return `Khách lẻ phải trả đủ ${money(payable.value)}, còn thiếu ${money(remain.value)}. Muốn ghi nợ thì chọn hoặc tạo khách hàng.`
  return ''
})

// Nut "Cap nhat" no cu trong PayDialog cu: dat lai so no cua khach (chi quan tri)
// Nut "Cap nhat" no cu (PayDialog cu): dieu chinh no co ly do (hien tren phieu thu / sao ke)
const adjustOpen = ref(false)
function setCustomer(c: Customer) {
  customer.value = c
  customers.value = customers.value.map((x) => (x.id === c.id ? c : x))
}

function payAll() { pay.paidAmount = payable.value }
function payGoodsOnly() { pay.paidAmount = itemsTotal.value + Number(pay.shippingFee || 0) }

// Bam "Ghi no het" = chu y ghi no toan bo -> khong hoi lai. Doi so tien / doi khach thi bo.
const debtAllChosen = ref(false)
watch(() => pay.paidAmount, (v) => { if (v) debtAllChosen.value = false })
watch(() => customer.value?.id, () => { debtAllChosen.value = false })
function debtAll() { pay.paidAmount = 0; debtAllChosen.value = true }

async function checkout() {
  if (!order.value?.items.length || busy.value) return
  if (needName.value) return ElMessage.warning('Vui lòng chọn khách hàng hoặc nhập tên khách lẻ')
  // Khach quen, o Khach tra van 0 (thu ngan co the quen nhap) -> hoi 1 lan truoc khi ghi no toan bo
  if (customer.value && !Number(pay.paidAmount) && remain.value > 0 && !debtAllChosen.value) {
    const ok = await ElMessageBox.confirm(
      `Khách chưa trả đồng nào. Ghi nợ toàn bộ ${money(remain.value)} cho ${customer.value.name}?`,
      'Ghi nợ hết', { confirmButtonText: 'Ghi nợ hết (Enter)', cancelButtonText: 'Nhập tiền khách trả', type: 'warning', distinguishCancelAndClose: true },
    ).then(() => true, () => false)
    if (!ok) return nextTick(() => document.getElementById('paid')?.focus())
  }
  busy.value = true
  try {
    const { data } = await http.post<Order>(`/orders/${order.value.id}/checkout`, {
      customerId: customer.value?.id ?? null,
      customerName: customer.value ? null : pay.walkInName.trim(),
      customerPhone: customer.value ? null : pay.walkInPhone,
      shippingFee: pay.shippingFee || 0,
      paidAmount: pay.paidAmount || 0,
      note: pay.note,
      // no cu dang hien tren phieu: may khac vua doi no cua khach thi server tu choi (409), tranh hoa don sai no cu
      expectedPreviousDebt: customer.value ? Number(customer.value.debt) : null,
    })
    done.value = data
    sessionStorage.removeItem(DRAFT_KEY)
    ElMessage.success(`Đã chốt ${data.code}`)
  } catch (e: any) {
    if (e?.response?.status === 409 && customer.value) {
      setCustomer((await http.get<Customer>(`/customers/${customer.value.id}`)).data) // tai lai no cu, thu ngan xem lai roi chot
    }
  } finally {
    busy.value = false
  }
}

// In hoa don bang mau Jasper cu (Bill.jrxml) - backend tra ve PDF
function print() { openPdf(`/orders/${done.value!.id}/invoice.pdf`) }
const zalo = ref(false)

// Dong tab / tai lai khi hoa don nhap con trong -> xoa luon (keepalive de request van gui khi trang dong)
function dropEmptyDraft() {
  if (order.value && !done.value && order.value.items.length === 0) {
    fetch(`/api/orders/${order.value.id}`, { method: 'DELETE', keepalive: true, headers: { Authorization: `Bearer ${localStorage.getItem('token')}` } }).catch(() => {})
    sessionStorage.removeItem(DRAFT_KEY)
  }
}

// Man hep: thanh "Tong phai tra" dinh day man, bam de cuon toi phieu thanh toan
const ticketEl = ref<HTMLElement>()
function goToTicket() { ticketEl.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }

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

// Roi man hinh / dong tab khi hoa don nhap con trong thi xoa luon
onMounted(() => window.addEventListener('pagehide', dropEmptyDraft))
onBeforeUnmount(() => {
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

      <form class="entry panel" :class="{ disabled: !!done }" @submit.prevent="addItem" @keydown.esc="clearLine" @focusout="onEntryOut">
        <div class="f product" @keydown.enter.capture="onProductEnter">
          <label for="pick-product">Sản phẩm <kbd>F2</kbd></label>
          <el-select id="pick-product" ref="productSelect" v-model="line.productId" filterable remote :remote-method="searchProducts"
                     default-first-option :debounce="0" :loading="productLoading" placeholder="Gõ tên, không cần dấu (vd: ton 0,4)"
                     :disabled="!!done" popper-class="product-popper" @change="onPickProduct">
            <el-option v-for="p in products" :key="p.id" :value="p.id" :label="p.name">
              <div class="opt">
                <span class="opt-name">
                  <template v-for="(seg, i) in highlight(p.name, productQuery)" :key="i"><mark v-if="seg.m">{{ seg.t }}</mark><template v-else>{{ seg.t }}</template></template>
                  <small v-if="p.categoryName" class="muted"> · {{ p.categoryName }}</small>
                  <small v-if="p.boughtCount" class="bought"> · khách đã mua {{ p.boughtCount }} lần</small>
                </span>
                <span class="num muted">{{ num(p.price) }}/{{ p.unit || 'đv' }} · <span :class="{ 'tone-danger': p.onHand != null && Number(p.onHand) <= 0 }">tồn {{ p.onHand == null ? '—' : qty(p.onHand) }}</span></span>
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
        <div v-if="isSheet && !done" class="f segs">
          <label for="segs">Nhiều đoạn <span class="muted small">mét×tấm (vd 4,7x2), cách nhau dấu cách · Enter thêm hết · để trống + Enter / Tab: nhập từng dòng</span></label>
          <div class="segs-row">
            <el-input id="segs" ref="segField" v-model="segText" placeholder="vd: 4,7x2 4,5x2 3x1" autocomplete="off"
                      @keydown.enter.prevent.stop="addSegments" />
            <span v-if="segText.trim()" class="segs-preview" aria-live="polite">
              <span v-if="segParsed.error" class="line-problem">{{ segParsed.error }}</span>
              <template v-else>
                {{ segParsed.segments.length }} đoạn<template v-if="segPreview.sheets"> · {{ segPreview.sheets }} tấm</template> · <b class="num">{{ qty(segPreview.totalQuantity) }}</b> m
                · <b class="num">{{ money(segPreview.lineTotal) }}</b>
                <span v-if="segPreview.sheets" class="muted small"> (hệ số {{ qty(segFactor) }})</span>
              </template>
            </span>
          </div>
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
          <label for="p">Đơn giá
            <!-- tabindex -1: Tab tu He so phai vao thang o Don gia, khong dung o nut nay (Enter se ve gia niem yet) -->
            <button v-if="priceEdited && !done" type="button" tabindex="-1" class="price-note" :title="`Giá niêm yết ${num(listPrice)}. Bấm để dùng lại giá niêm yết.`"
                    @click="line.unitPrice = listPrice!">đã sửa · niêm yết {{ num(listPrice) }}</button>
          </label>
          <NumberField id="p" v-model="line.unitPrice" kind="integer" :step="1000" :disabled="!!done" />
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
          <span v-if="lineProblem" class="line-problem" role="alert">{{ lineProblem }}</span>
          <span v-else-if="line.productId && order.items.length" class="muted small keep-hint">Esc: chọn sản phẩm khác</span>
        </div>
        <el-button type="primary" native-type="submit" :icon="Plus" :loading="busy" :disabled="!!done || !!lineProblem" class="add">Thêm vào hóa đơn</el-button>
      </form>

      <div class="panel">
        <ul v-if="narrow" class="cards" aria-label="Dòng hàng">
          <li v-if="!tableRows.length" class="muted empty-card">Chưa có mặt hàng. Chọn sản phẩm ở trên rồi bấm Thêm.</li>
          <li v-for="row in tableRows" :key="row.rowKey" class="card" :class="{ group: row.isGroup }">
            <div class="card-top">
              <span class="card-name">{{ row.no }}. {{ row.productName }}<span v-if="row.unit" class="muted"> · {{ row.unit }}</span></span>
              <b class="num line-total">{{ num(row.lineTotal) }}</b>
            </div>
            <div class="card-sub muted">
              <template v-if="row.isGroup">{{ row.count }} đoạn: {{ row.segs }} = <b class="num">{{ qty(row.totalQuantity) }}</b><template v-if="row.sheets"> · {{ row.sheets }} tấm</template></template>
              <template v-else>{{ qtyText(row) }}<template v-if="row.sheets"> = <b class="num">{{ qty(row.totalQuantity) }}</b></template></template>
              × {{ num(row.unitPrice) }}
            </div>
            <div v-if="!done" class="card-actions">
              <template v-if="row.isGroup">
                <el-button size="small" text @click="toggleGroup(row.rowKey)">{{ openGroups.has(row.rowKey) ? 'Thu gọn' : `Sửa từng đoạn (${row.count})` }}</el-button>
                <el-button size="small" text type="danger" :icon="Delete" :aria-label="`Xóa cả ${row.count} đoạn ${row.productName}`" @click="removeGroup(row)">Xóa cụm</el-button>
              </template>
              <div v-else class="row-actions" :data-item="row.id">
                <el-button size="small" text :icon="Edit" :aria-label="`Sửa ${row.productName}`" @click="openEdit(row)">Sửa</el-button>
                <el-button size="small" text type="danger" :icon="Delete" :aria-label="`Xóa ${row.productName}`" @click="removeItem(row)">Xóa</el-button>
              </div>
            </div>
            <ul v-if="row.isGroup && openGroups.has(row.rowKey)" class="segs-list">
              <li v-for="c in row.children" :key="c.rowKey">
                <span>Đoạn {{ c.seg }}: {{ qtyText(c) }}</span>
                <b class="num line-total">{{ num(c.lineTotal) }}</b>
                <div v-if="!done" class="row-actions" :data-item="c.id">
                  <el-button size="small" text :icon="Edit" :aria-label="`Sửa ${c.productName} đoạn ${c.seg}`" @click="openEdit(c)" />
                  <el-button size="small" text type="danger" :icon="Delete" :aria-label="`Xóa ${c.productName} đoạn ${c.seg}`" @click="removeItem(c)" />
                </div>
              </li>
            </ul>
          </li>
        </ul>
        <el-table v-else :data="tableRows" row-key="rowKey" :tree-props="{ children: 'children' }" :indent="12"
                  empty-text="Chưa có mặt hàng. Chọn sản phẩm ở trên rồi bấm Thêm (hoặc Enter)." style="width: 100%" :row-class-name="rowClass">
          <el-table-column label="Mặt hàng" min-width="160">
            <template #default="{ row }">
              <template v-if="row.seg"><span class="muted">Đoạn {{ row.seg }}</span></template>
              <span v-else class="name-wrap">
                <div class="one-line" :title="row.productName"><span class="muted no">{{ row.no }}.</span> {{ row.productName }}<span v-if="row.unit" class="muted"> · {{ row.unit }}</span></div>
                <div v-if="row.isGroup" class="one-line segs-sum muted" :title="row.segs">{{ row.count }} đoạn: {{ row.segs }}</div>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="SL" width="72" align="right"><template #default="{ row }"><span v-if="!row.isGroup" class="num">{{ qty(row.quantity) }}</span></template></el-table-column>
          <el-table-column label="Tổng SL" width="112" align="right">
            <template #default="{ row }">
              <span class="num">{{ qty(row.totalQuantity) }}</span>
              <div v-if="row.isGroup && row.sheets" class="formula num">{{ row.sheets }} tấm</div>
              <div v-else-if="row.sheets" class="formula num" :title="`Số tấm ${row.sheets}, hệ số ${qty(row.factor)}`">× {{ row.sheets }} tấm × {{ qty(row.factor) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Đơn giá" width="100" align="right"><template #default="{ row }"><span class="num">{{ num(row.unitPrice) }}</span></template></el-table-column>
          <el-table-column label="Thành tiền" width="120" align="right"><template #default="{ row }"><b class="num line-total">{{ num(row.lineTotal) }}</b></template></el-table-column>
          <el-table-column v-if="!done" label="Sửa / xóa" width="104" align="center">
            <template #default="{ row }">
              <div v-if="row.isGroup" class="group-actions">
                <el-button text type="danger" :icon="Delete" :aria-label="`Xóa cả ${row.count} đoạn ${row.productName}`" @click="removeGroup(row)" />
              </div>
              <div v-else class="row-actions" :data-item="row.id">
                <el-button text :icon="Edit" :aria-label="`Sửa ${row.productName}${row.seg ? ' đoạn ' + row.seg : ''}`" @click="openEdit(row)" />
                <el-button text type="danger" :icon="Delete" :aria-label="`Xóa ${row.productName}${row.seg ? ' đoạn ' + row.seg : ''}`" @click="removeItem(row)" />
              </div>
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
          <el-button v-if="!done" link type="primary" :icon="Plus" @click="newCus.open = true">Khách mới</el-button>
        </div>
        <el-select id="cus" ref="customerSelect" v-model="customer" value-key="id" filterable remote clearable default-first-option :remote-method="searchCustomers"
                   placeholder="Khách lẻ (trả đủ, không ghi nợ)" :disabled="!!done" style="width: 100%">
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
          <dt>Nợ cũ <el-button v-if="customer && auth.isAdmin && !done" link type="primary" size="small" @click="adjustOpen = true">Cập nhật</el-button></dt>
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
          <el-button v-if="customer" size="small" @click="debtAll">Ghi nợ hết</el-button>
        </div>
        <div class="remain" :class="{ owe: remain > 0 }">
          <dt>{{ remain > 0 ? (customer ? 'Còn nợ sau đơn' : 'Còn thiếu') : 'Còn lại' }}</dt>
          <dd class="num">{{ num(remain) }}</dd>
        </div>
        <div v-if="change > 0"><dt>Tiền thừa trả khách</dt><dd class="num">{{ num(change) }}</dd></div>
      </dl>

      <el-input v-model="pay.note" type="textarea" :rows="2" placeholder="Ghi chú (giao hàng, cắt theo kích thước...)" aria-label="Ghi chú" :disabled="!!done" />

      <template v-if="!done">
        <p v-if="blockReason" id="block-reason" class="hint warn" role="status">{{ blockReason }}</p>
        <el-button ref="ctaButton" type="success" size="large" class="cta" :loading="busy" :disabled="!!blockReason"
                   :aria-describedby="blockReason ? 'block-reason' : 'cta-note'" @click="checkout">
          Chốt hóa đơn <kbd class="on-dark">F9</kbd>
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
          <el-button type="success" size="large" :icon="ChatDotRound" @click="zalo = true">Gửi Zalo</el-button>
          <el-button size="large" :icon="DocumentAdd" class="wide" @click="startNew">Hóa đơn mới</el-button>
        </div>
        <ZaloSendDialog v-model="zalo" :title="`Hóa đơn ${done.code}`" :image-path="`/orders/${done.id}/invoice.png`"
                        :file-name="`${done.code}.png`" :text="orderMessage(done)" :phone="done.customerPhone" />
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

    <div v-if="!done" class="paybar">
      <div><span class="muted">Tổng phải trả</span> <b class="num">{{ money(payable) }}</b></div>
      <el-button type="success" :disabled="!order.items.length" @click="goToTicket">Thanh toán</el-button>
    </div>

    <DebtAdjustDialog v-model="adjustOpen" :customer="customer" @saved="setCustomer" />

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
.sale { display: grid; grid-template-columns: minmax(0, 1fr) clamp(300px, 30vw, 360px); gap: var(--space-xl); align-items: start; }
/* cot trai tu co gian theo be rong that cua no (container query), khong theo man hinh */
.left { display: flex; flex-direction: column; gap: var(--space-lg); min-width: 0; container-type: inline-size; }
.head { display: flex; justify-content: space-between; align-items: flex-end; gap: var(--space-lg); flex-wrap: wrap; }
.head h1 { font-size: 22px; }
.head p { margin: 2px 0 0; font-size: 14px; }

.entry { display: grid; grid-auto-flow: row dense; grid-template-columns: minmax(160px, 2fr) repeat(4, minmax(100px, 1fr)); gap: var(--space-md) var(--space-lg); padding: var(--space-lg) var(--space-xl); align-items: end; }
@container (max-width: 700px) {
  .entry { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .f.product { grid-column: 1 / -1; }
  .calc { grid-column: 1 / -1 !important; }
  .add { grid-column: 1 / -1 !important; }
}
@container (max-width: 420px) { .entry { grid-template-columns: 1fr 1fr; } }
.paybar { display: none; }
.stockinfo { font-size: 13px; color: var(--ink-muted); margin-left: auto; }
.stockinfo.over { color: var(--debt); font-weight: 600; }
.entry.disabled { opacity: 0.6; }
.f { display: flex; flex-direction: column; gap: var(--space-sm); min-width: 0; }
.f label, .lbl { font-size: 13px; font-weight: 500; color: #334155; }
.f :deep(.el-select) { width: 100%; }
.segs { grid-column: 1 / -1; }
.price-note { margin-left: 6px; padding: 0 6px; border: 1px solid #FCD34D; border-radius: 10px; background: #FFFBEB; color: #92400E;
  font-size: 11px; font-weight: 500; cursor: pointer; line-height: 18px; }
.price-note:hover { background: #FEF3C7; }
.segs-row { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-sm) var(--space-md); }
.segs-row .el-input { flex: 0 1 320px; }
.segs-preview { font-size: 14px; }
.segs label .small { font-weight: 400; font-size: 12px; }
.name-wrap { display: inline-block; vertical-align: top; max-width: calc(100% - 24px); }
.one-line { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.segs-sum { font-size: 12px; }
.no { font-variant-numeric: tabular-nums; }
.cards { list-style: none; margin: 0; padding: 0; }
.card { padding: var(--space-md) var(--space-lg); border-bottom: 1px solid var(--steel-100); }
.card.group { background: #F8FAFC; }
.card-top { display: flex; justify-content: space-between; align-items: baseline; gap: var(--space-md); }
.card-name { min-width: 0; font-weight: 500; }
.card-sub { font-size: 13px; margin-top: 2px; overflow-wrap: anywhere; }
.card-actions { display: flex; justify-content: flex-end; gap: var(--space-sm); margin-top: 2px; }
.card-actions .el-button + .el-button { margin-left: 0; }
.empty-card { padding: var(--space-lg); text-align: center; }
.segs-list { list-style: none; margin: var(--space-sm) 0 0; padding: 0 0 0 var(--space-md); border-left: 2px solid var(--steel-200); }
.segs-list li { display: flex; align-items: center; gap: var(--space-sm); font-size: 13px; }
.segs-list li span { flex: 1; }
:deep(.group-row) td { background: #F8FAFC; }
:deep(.seg-row) td { font-size: 13px; }
.calc { grid-column: 1 / -3; display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--space-sm) var(--space-md); font-size: 14px; padding-top: var(--space-sm); }
.calc b { font-size: 16px; }
.calc .small { font-size: 12px; }
.calc .sep { width: 1px; height: 14px; background: var(--steel-200); margin: 0 var(--space-sm); }
.add { grid-column: -3 / -1; }
.line-problem { color: var(--danger); font-weight: 500; flex-basis: 100%; }
.keep-hint { margin-left: auto; }
kbd { font-family: var(--font-mono); font-size: 11px; font-weight: 500; color: var(--ink-muted); border: 1px solid var(--steel-200); border-bottom-width: 2px; border-radius: 4px; padding: 0 4px; margin-left: 4px; background: var(--surface); }
kbd.on-dark { color: inherit; background: transparent; border-color: rgba(255, 255, 255, 0.5); }
.opt { display: flex; justify-content: space-between; gap: var(--space-lg); }
.opt-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
.opt-name .bought { color: #047857; font-weight: 600; }
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
.done-actions .wide { grid-column: 1 / -1; }
.edit-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 var(--space-lg); }


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
