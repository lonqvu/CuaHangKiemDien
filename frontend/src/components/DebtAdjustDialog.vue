<script setup lang="ts">
/*
 * Dieu chinh so no (o "No" man Customers cu, nut "Cap nhat" no cu o PayDialog cu).
 * Nhu cac phan mem ban hang lon: nhap SO TIEN thay doi (Tang / Giam), may tu tinh so no moi; "Sua so no" khi da doi chieu
 * xong va biet con so dung. Bat buoc ly do: so no / sao ke ghi "Tang no: <ly do>" / "Giam no: <ly do>".
 * Khach TRA TIEN thi dung Thu no (co phieu thu, tinh vao tien thu), khong dung Giam no.
 */
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import type { Customer } from '../api/types'
import { money, num } from '../format'
import NumberField from './NumberField.vue'

const props = defineProps<{ customer: Customer | null; modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [Customer] }>()

type Mode = 'up' | 'down' | 'set'
const form = reactive({ mode: 'up' as Mode, amount: 0, debt: 0, reason: '' })
const saving = ref(false)
const amountField = ref()

const REASONS: Record<Mode, string[]> = {
  up: ['Nợ cũ ghi sổ tay chưa nhập', 'Phí cắt / vận chuyển tính thêm', 'Ghi thiếu trước đó'],
  down: ['Bớt số lẻ cho khách', 'Chiết khấu / giảm giá', 'Hàng lỗi, khách trả lại', 'Ghi thừa trước đó'],
  set: ['Đối chiếu sổ với khách', 'Nợ cũ chuyển từ sổ tay / phần mềm cũ'],
}

const current = computed(() => Number(props.customer?.debt ?? 0))

watch(() => props.modelValue, (open) => {
  if (open && props.customer) {
    Object.assign(form, { mode: 'up', amount: 0, debt: current.value, reason: '' })
    focus()
  }
})
function focus() { nextTick(() => amountField.value?.focus()) }
function setMode(m: Mode) {
  if (m === 'down' && current.value <= 0) return
  if (REASONS[form.mode].includes(form.reason)) form.reason = '' // ly do goi y cua kieu cu khong con hop
  form.mode = m
  form.amount = 0
  form.debt = current.value
  focus()
}

/** So no sau dieu chinh. */
const after = computed(() => form.mode === 'up' ? current.value + (form.amount || 0)
  : form.mode === 'down' ? current.value - (form.amount || 0) : (form.debt || 0))
const diff = computed(() => after.value - current.value)
const tooMuch = computed(() => form.mode === 'down' && (form.amount || 0) > current.value)

// nut so nhanh
const quickUp = [100_000, 500_000, 1_000_000, 5_000_000]
const oddPart = computed(() => { // bo so le: duoi 1 trieu (no >= 1 trieu) hoac duoi 1 nghin
  const unit = current.value >= 1_000_000 ? 1_000_000 : 1_000
  return current.value % unit
})
function pickOdd() {
  form.amount = oddPart.value
  if (!form.reason) form.reason = 'Bớt số lẻ cho khách'
}
const quickDown = computed(() => [100_000, 500_000].filter((v) => v < current.value))

const actionLabel = computed(() => form.mode === 'up' ? `Tăng nợ ${num(form.amount)} ₫`
  : form.mode === 'down' ? `Giảm nợ ${num(form.amount)} ₫` : `Lưu số nợ ${num(form.debt)} ₫`)
const canSave = computed(() => diff.value !== 0 && !tooMuch.value && after.value >= 0 && !!form.reason.trim())

async function save() {
  if (!props.customer) return
  if (diff.value === 0) return ElMessage.warning(form.mode === 'set' ? 'Số nợ không đổi' : 'Nhập số tiền')
  if (tooMuch.value) return ElMessage.warning(`Giảm tối đa ${money(current.value)} (số đang nợ)`)
  if (!form.reason.trim()) return ElMessage.warning('Nhập lý do (in trên sao kê)')
  saving.value = true
  try {
    const { data } = await http.post<Customer>(`/customers/${props.customer.id}/debt-adjustments`, { debt: after.value, note: form.reason.trim() })
    ElMessage.success(`Đã ${diff.value > 0 ? 'tăng' : 'giảm'} nợ ${money(Math.abs(diff.value))}. Nợ hiện tại ${money(data.debt)}`)
    emit('saved', data)
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="`Điều chỉnh nợ: ${customer?.name ?? ''}`" width="min(460px, 94vw)" @update:model-value="emit('update:modelValue', $event)">
    <el-form label-position="top" @submit.prevent="save">
      <p class="now">Nợ hiện tại: <b class="num">{{ money(current) }}</b></p>

      <div class="seg" role="radiogroup" aria-label="Kiểu điều chỉnh">
        <button type="button" role="radio" :aria-checked="form.mode === 'up'" :class="{ on: form.mode === 'up', up: true }" @click="setMode('up')">＋ Tăng nợ</button>
        <button type="button" role="radio" :aria-checked="form.mode === 'down'" :class="{ on: form.mode === 'down', down: true }"
                :disabled="current <= 0" :title="current <= 0 ? 'Khách không nợ' : undefined" @click="setMode('down')">－ Giảm nợ</button>
        <button type="button" role="radio" :aria-checked="form.mode === 'set'" :class="{ on: form.mode === 'set', set: true }" @click="setMode('set')">＝ Sửa số nợ</button>
      </div>

      <el-form-item v-if="form.mode !== 'set'" :label="form.mode === 'up' ? 'Số tiền tăng thêm (₫)' : 'Số tiền giảm (₫)'" for="adj-amount">
        <NumberField id="adj-amount" ref="amountField" v-model="form.amount" kind="integer" :step="100000" class="big" />
        <div class="quick">
          <template v-if="form.mode === 'up'">
            <button v-for="v in quickUp" :key="v" type="button" class="num" @click="form.amount = v">+{{ num(v) }}</button>
          </template>
          <template v-else>
            <button v-if="oddPart > 0" type="button" @click="pickOdd">Bỏ số lẻ: −<span class="num">{{ num(oddPart) }}</span></button>
            <button v-for="v in quickDown" :key="v" type="button" class="num" @click="form.amount = v">−{{ num(v) }}</button>
            <button type="button" @click="form.amount = current">Xóa hết nợ</button>
          </template>
        </div>
      </el-form-item>
      <el-form-item v-else label="Số nợ đúng (₫)" for="adj-debt">
        <NumberField id="adj-debt" ref="amountField" v-model="form.debt" kind="integer" :step="100000" class="big" />
        <p class="hint">Dùng khi đã đối chiếu với khách và chốt được con số đúng.</p>
      </el-form-item>

      <p v-if="form.mode === 'down'" class="warn">Khách <b>trả tiền</b> thì dùng nút <b>Thu nợ</b> (có phiếu thu), không dùng Giảm nợ.</p>

      <div class="pv" aria-live="polite">
        <div><span>Nợ hiện tại</span><span class="num">{{ num(current) }}</span></div>
        <div :class="diff > 0 ? 'tone-debt' : diff < 0 ? 'tone-money' : 'muted'">
          <span>{{ form.mode === 'set' ? 'Chênh lệch' : form.mode === 'up' ? 'Tăng' : 'Giảm' }}</span>
          <span class="num">{{ diff > 0 ? '+ ' : diff < 0 ? '− ' : '' }}{{ num(Math.abs(diff)) }}</span>
        </div>
        <div class="b"><span>Nợ sau điều chỉnh</span>
          <span class="num" :class="{ 'tone-debt': diff > 0, 'tone-money': diff < 0 }">{{ tooMuch ? '—' : num(after) }}</span></div>
        <p v-if="tooMuch" class="err" role="alert">Giảm tối đa {{ money(current) }} (số đang nợ).</p>
      </div>

      <el-form-item for="adj-reason" required>
        <template #label>Lý do <span class="muted">(in trên sao kê)</span></template>
        <el-input id="adj-reason" v-model="form.reason" maxlength="200" show-word-limit
                  :placeholder="form.mode === 'up' ? 'VD: Nợ cũ ghi sổ tay chưa nhập' : form.mode === 'down' ? 'VD: Bớt số lẻ cho khách' : 'VD: Đối chiếu sổ với khách ngày 23/11'" />
        <div class="chips">
          <button v-for="r in REASONS[form.mode]" :key="r" type="button" :class="{ sel: form.reason === r }" @click="form.reason = r">{{ r }}</button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">Hủy</el-button>
      <el-button type="primary" :loading="saving" :disabled="!canSave" @click="save">{{ actionLabel }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.now { margin: 0 0 var(--space-md); }
.seg { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); border: 1px solid var(--steel-200); border-radius: var(--radius-md); overflow: hidden; margin-bottom: var(--space-lg); }
.seg button { font: inherit; font-size: 14px; padding: 9px 4px; background: var(--surface, #fff); color: var(--ink); border: 0; border-right: 1px solid var(--steel-200); cursor: pointer; min-height: 40px; }
.seg button:last-child { border-right: 0; }
.seg button:disabled { color: var(--ink-muted); cursor: not-allowed; opacity: .6; }
.seg button.on { font-weight: 600; }
.seg .up.on { background: #FEF2F2; color: var(--debt, #B91C1C); }
.seg .down.on { background: #F0FDF4; color: var(--money, #15803D); }
.seg .set.on { background: var(--steel-50); color: var(--steel-900, #16243A); }
.big :deep(input) { font-size: 18px; }
.quick, .chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: var(--space-sm); }
.quick button, .chips button { font: inherit; font-size: 13px; padding: 4px 10px; border: 1px solid var(--steel-200); background: var(--surface, #fff); color: var(--ink); border-radius: 14px; cursor: pointer; }
.chips button { border-radius: 6px; }
.quick button:hover, .chips button:hover { border-color: var(--steel-400, #94A3B8); }
.chips button.sel { border-color: var(--steel-900, #16243A); background: var(--steel-50); font-weight: 600; }
.hint { margin: 6px 0 0; font-size: 13px; color: var(--ink-muted); }
.warn { margin: 0 0 var(--space-md); font-size: 13px; color: #B45309; background: #FFFBEB; border-radius: var(--radius-md); padding: 7px 10px; }
.pv { background: var(--steel-50); border-radius: var(--radius-md); padding: var(--space-md) var(--space-lg); margin-bottom: var(--space-lg); font-size: 14px; }
.pv > div { display: flex; justify-content: space-between; padding: 3px 0; }
.pv .b { font-weight: 700; font-size: 16px; border-top: 1px solid var(--steel-200); margin-top: 4px; padding-top: 7px; }
.err { margin: 6px 0 0; color: var(--debt); font-size: 13px; }
</style>
