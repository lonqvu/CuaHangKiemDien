<script setup lang="ts">
/*
 * Sua hoa don DA CHOT (chi quan tri). Giu ma hoa don; may chu tinh lai tien hang, tong thanh toan, con no,
 * cong no khach (chenh lech ghi vao so no), kho (xuat them / nhap lai phan chenh lech) va luu lich su sua.
 * Moi lan doi o nhap -> hoi may chu "xem truoc" (chua luu) de thay ngay tien / no thay doi the nao.
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import http, { errorMessage } from '../api/http'
import type { Order, Page, Product } from '../api/types'
import { debounce, num, qty } from '../format'
import NumberField from './NumberField.vue'
import { useNarrow } from '../composables/useNarrow'

const props = defineProps<{ modelValue: boolean; order: Order }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; saved: [o: Order] }>()
const narrow = useNarrow()

interface Row { key: number; id: number | null; productId: number | null; productName: string; unit: string | null
  quantity: number | null; sheets: number | null; factor: number | null; unitPrice: number | null }
interface Effect {
  lines: { lineTotal: number; totalQuantity: number }[]
  itemsTotalBefore: number; itemsTotalAfter: number; shippingFee: number; previousDebt: number
  totalPayableBefore: number; totalPayableAfter: number; paidBefore: number; paidAfter: number
  debtAfterBefore: number; debtAfterAfter: number; customerDebtBefore: number | null; customerDebtAfter: number | null
  debtChange: number; refund: number
}

let seq = 0
const rows = ref<Row[]>([])
const ship = ref<number | null>(0)
const paid = ref<number | null>(0)
const note = ref('')
const reason = ref('')
const effect = ref<Effect | null>(null)
const previewError = ref('')
const previewing = ref(false)
const saving = ref(false)
const walkIn = computed(() => !props.order.customerId)

function reset() {
  rows.value = props.order.items.map((i) => ({ key: ++seq, id: i.id, productId: i.productId, productName: i.productName, unit: i.unit,
    quantity: Number(i.quantity), sheets: i.sheets, factor: Number(i.factor), unitPrice: Number(i.unitPrice) }))
  ship.value = Number(props.order.shippingFee)
  paid.value = Number(props.order.paidAmount)
  note.value = props.order.note ?? ''
  reason.value = ''
  effect.value = null
  previewError.value = ''
}
watch(() => props.modelValue, (v) => { if (v) { reset(); preview() } })

const body = () => ({
  items: rows.value.map((r) => ({ id: r.id, productId: r.productId, quantity: r.quantity, sheets: r.sheets ?? 0, factor: r.factor, unitPrice: r.unitPrice })),
  shippingFee: ship.value ?? 0, paidAmount: paid.value ?? 0, note: note.value, reason: reason.value,
})

let pseq = 0
const preview = debounce(async () => {
  const s = ++pseq
  if (rows.value.some((r) => !r.productId)) { previewError.value = 'Chọn sản phẩm cho dòng mới'; effect.value = null; return }
  previewing.value = true
  try {
    const { data } = await http.post<Effect>(`/orders/${props.order.id}/revise-preview`, body(), { silent: true } as any)
    if (s !== pseq) return
    effect.value = data
    previewError.value = ''
  } catch (e) {
    if (s !== pseq) return
    effect.value = null
    previewError.value = errorMessage(e)
  } finally {
    if (s === pseq) previewing.value = false
  }
}, 350)
watch([rows, ship, paid], () => { if (props.modelValue) preview() }, { deep: true })

// ---- them dong: tim san pham
const options = ref<Product[]>([])
const searching = ref(false)
const searchProducts = debounce(async (q: string) => {
  searching.value = true
  try { options.value = (await http.get<Page<Product>>('/products', { params: { q, size: 30 } })).data.items }
  finally { searching.value = false }
}, 250)
function addRow() {
  rows.value.push({ key: ++seq, id: null, productId: null, productName: '', unit: null, quantity: 1, sheets: 0, factor: 0, unitPrice: null })
  searchProducts('')
}
function pick(r: Row, id: number) {
  const p = options.value.find((x) => x.id === id)
  if (!p) return
  r.productId = p.id
  r.productName = p.name
  r.unit = p.unit
  if (r.id === null || r.unitPrice == null) r.unitPrice = Number(p.price)
}
function removeRow(r: Row) { rows.value = rows.value.filter((x) => x !== r) }

const lineTotal = (i: number) => effect.value?.lines[i]?.lineTotal
const changed = (a?: number | null, b?: number | null) => Number(a ?? 0) !== Number(b ?? 0)

async function save() {
  if (!reason.value.trim()) { ElMessage.warning('Nhập lý do sửa (để sau này biết vì sao hóa đơn đổi)'); return }
  saving.value = true
  try {
    const { data } = await http.post<Order>(`/orders/${props.order.id}/revise`, body())
    ElMessage.success(`Đã sửa hóa đơn ${data.code}`)
    emit('saved', data)
    emit('update:modelValue', false)
  } catch { /* interceptor da bao */ } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="`Sửa hóa đơn ${order.code}`" :fullscreen="narrow" width="min(980px, 96vw)"
             :close-on-click-modal="false" @update:model-value="emit('update:modelValue', $event)">
    <div class="rv">
      <p class="muted small">Khách: <b>{{ order.customerName || 'Khách lẻ' }}</b>{{ walkIn ? ' (khách lẻ: phải trả đủ)' : '' }}.
        Lưu xong hệ thống tự tính lại tiền, công nợ, kho và giữ nguyên mã hóa đơn.</p>

      <!-- dong hang -->
      <div class="lines">
        <div v-if="!narrow" class="ln head">
          <span>Mặt hàng</span><span>Số lượng</span><span>Số tấm</span><span>Hệ số</span><span>Đơn giá</span><span class="r">Thành tiền</span><span />
        </div>
        <div v-for="(r, i) in rows" :key="r.key" class="ln">
          <div class="name">
            <template v-if="r.id !== null">
              <b>{{ r.productName }}</b> <span v-if="r.unit" class="muted small">({{ r.unit }})</span>
            </template>
            <el-select v-else :model-value="r.productId ?? undefined" filterable remote :remote-method="searchProducts" :loading="searching"
                       placeholder="Tìm sản phẩm..." aria-label="Chọn sản phẩm" class="full" @update:model-value="pick(r, $event)">
              <el-option v-for="p in options" :key="p.id" :value="p.id" :label="p.name">
                <span>{{ p.name }}</span> <span class="muted small">{{ num(p.price) }}/{{ p.unit }}</span>
              </el-option>
            </el-select>
          </div>
          <label><span v-if="narrow">Số lượng</span><NumberField v-model="r.quantity" kind="decimal" :min="0" aria-label="Số lượng" /></label>
          <label><span v-if="narrow">Số tấm</span><NumberField v-model="r.sheets" kind="integer" :min="0" aria-label="Số tấm" /></label>
          <label><span v-if="narrow">Hệ số</span><NumberField v-model="r.factor" kind="decimal" :min="0" aria-label="Hệ số" /></label>
          <label><span v-if="narrow">Đơn giá</span><NumberField v-model="r.unitPrice" kind="integer" :min="0" aria-label="Đơn giá" /></label>
          <div class="total num">
            <span v-if="narrow" class="muted small">Thành tiền </span>
            {{ lineTotal(i) != null ? num(lineTotal(i)) : '…' }}
            <span v-if="effect?.lines[i] && Number(r.sheets) > 0" class="muted small block">Tổng SL {{ qty(effect.lines[i].totalQuantity) }}</span>
          </div>
          <el-button :icon="Delete" text type="danger" :aria-label="`Bỏ dòng ${r.productName || 'mới'}`" :disabled="rows.length === 1" @click="removeRow(r)" />
        </div>
        <el-button :icon="Plus" plain class="add" @click="addRow">Thêm mặt hàng</el-button>
      </div>

      <div class="money">
        <label>Phí vận chuyển<NumberField v-model="ship" kind="integer" :min="0" /></label>
        <label>Khách trả lúc mua<NumberField v-model="paid" kind="integer" :min="0" /></label>
        <label class="wide">Ghi chú trên hóa đơn<el-input v-model="note" maxlength="300" /></label>
        <label class="wide"><span>Lý do sửa <span class="req">*</span></span><el-input v-model="reason" maxlength="300" placeholder="VD: khách trả lại 2 cây hộp, giao thiếu..." /></label>
      </div>

      <!-- xem truoc -->
      <div class="pv" :class="{ loading: previewing }" aria-live="polite">
        <p v-if="previewError" class="err" role="alert">{{ previewError }}</p>
        <template v-else-if="effect">
          <dl>
            <div><dt>Tiền hàng</dt><dd class="num"><s v-if="changed(effect.itemsTotalBefore, effect.itemsTotalAfter)">{{ num(effect.itemsTotalBefore) }}</s> {{ num(effect.itemsTotalAfter) }}</dd></div>
            <div v-if="!walkIn"><dt>Nợ cũ (giữ nguyên)</dt><dd class="num">{{ num(effect.previousDebt) }}</dd></div>
            <div><dt>Tổng thanh toán</dt><dd class="num"><s v-if="changed(effect.totalPayableBefore, effect.totalPayableAfter)">{{ num(effect.totalPayableBefore) }}</s> {{ num(effect.totalPayableAfter) }}</dd></div>
            <div><dt>Khách trả</dt><dd class="num"><s v-if="changed(effect.paidBefore, effect.paidAfter)">{{ num(effect.paidBefore) }}</s> {{ num(effect.paidAfter) }}</dd></div>
            <div class="b"><dt>Còn nợ trên hóa đơn</dt><dd class="num"><s v-if="changed(effect.debtAfterBefore, effect.debtAfterAfter)">{{ num(effect.debtAfterBefore) }}</s> {{ num(effect.debtAfterAfter) }}</dd></div>
            <div v-if="!walkIn" class="b"><dt>Nợ hiện tại của khách</dt>
              <dd class="num"><template v-if="changed(effect.debtChange, 0)">{{ num(effect.customerDebtBefore) }} → </template>
                <span :class="Number(effect.debtChange) > 0 ? 'tone-debt' : Number(effect.debtChange) < 0 ? 'tone-money' : ''">{{ num(effect.customerDebtAfter) }}</span>
                <span v-if="changed(effect.debtChange, 0)" class="muted small"> ({{ Number(effect.debtChange) > 0 ? '+' : '' }}{{ num(effect.debtChange) }})</span></dd></div>
          </dl>
          <p v-if="Number(effect.refund) > 0" class="refund">Khách đã trả hết nợ: cửa hàng <b>trả lại khách {{ num(effect.refund) }} đ tiền mặt</b>.</p>
        </template>
        <p v-else class="muted small">Đang tính...</p>
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">Không sửa</el-button>
      <el-button type="primary" :loading="saving" :disabled="!effect || !!previewError" @click="save">Lưu sửa hóa đơn</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.rv { display: flex; flex-direction: column; gap: var(--space-lg); }
.small { font-size: 13px; margin: 0; }
.block { display: block; }
.lines { display: flex; flex-direction: column; gap: var(--space-sm); }
.ln { display: grid; grid-template-columns: minmax(180px, 2fr) 1fr 0.7fr 0.7fr 1.1fr 1fr 32px; gap: var(--space-sm); align-items: center; }
.ln.head { font-size: 13px; font-weight: 600; color: var(--ink-muted); }
.ln .r, .total { text-align: right; }
.full { width: 100%; }
.add { align-self: flex-start; }
.money { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-md); }
.money label, .ln label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--ink-muted); }
.money .wide { grid-column: 1 / -1; }
.req { color: var(--debt); }
.pv { background: var(--steel-50); border-radius: var(--radius-md); padding: var(--space-md) var(--space-lg); transition: opacity .15s; }
.pv.loading { opacity: .6; }
.pv dl { margin: 0; display: grid; gap: 6px; }
.pv dl div { display: flex; justify-content: space-between; gap: var(--space-md); font-size: 14px; }
.pv dt { color: var(--ink-muted); }
.pv dd { margin: 0; text-align: right; }
.pv .b dd, .pv .b dt { font-weight: 600; color: var(--ink); }
.pv s { color: var(--ink-muted); margin-right: 4px; }
.err { margin: 0; color: var(--debt); font-size: 14px; }
.refund { margin: var(--space-sm) 0 0; font-size: 14px; color: var(--debt); }
@media (max-width: 760px) {
  .ln { grid-template-columns: 1fr 1fr; padding: var(--space-md); border: 1px solid var(--steel-200); border-radius: var(--radius-md); }
  .ln .name { grid-column: 1 / -1; }
  .ln .total { text-align: left; align-self: end; }
  .ln > .el-button { justify-self: end; align-self: end; }
  .money { grid-template-columns: 1fr; }
}
</style>
