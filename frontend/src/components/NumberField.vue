<script setup lang="ts">
/*
 * O nhap so cho nguoi Viet. el-input-number cua Element Plus hieu "4,2" thanh 42 (bo dau phay)
 * -> nhap 4,2 m tinh tien gap 10 lan. O nay:
 *  - kind="decimal" (so luong, he so): dau phay hoac dau cham deu la dau thap phan -> 4,2 = 4.2 = 4,2
 *  - kind="integer" (tien, so tam): chi lay chu so -> "95.000" hay "95,000" deu = 95000
 *  - so nguyen (tien) tu nhom nghin bang dau cham khi go: 1000000 -> 1.000.000
 *  - hien so le bang dau phay (4,2), khong ngan cach nghin (tranh doc 1.000 thanh mot)
 *  - focus thi boi den san de go de; mui ten len/xuong de tang/giam theo step
 *  - nullable: o rong = null (vd kiem ke "Chua dem" khac voi dem duoc 0), khong tu doi thanh 0
 */
import { computed, nextTick, ref, watch } from 'vue'
import { parseNumber, formatInput, groupDigits } from '../format'

const props = withDefaults(defineProps<{
  modelValue: number | string | null | undefined
  kind?: 'decimal' | 'integer'
  min?: number
  max?: number
  step?: number
  precision?: number
  id?: string
  placeholder?: string
  disabled?: boolean
  align?: 'left' | 'right'
  nullable?: boolean
  suffix?: string // don vi hien trong o (vd "/ chai") - khong lam o cao len, giu thang hang voi o khac
}>(), { kind: 'decimal', min: 0, step: 1, precision: 3, align: 'right', nullable: false })

const emit = defineEmits<{ 'update:modelValue': [number | null]; change: [number | null] }>()
const show = (v: number | string | null | undefined) => (props.nullable && v == null ? '' : formatInput(v, props.kind, props.precision))
function set(v: number | null) {
  emit('update:modelValue', v)
  emit('change', v)
}
const inputRef = ref()
const text = ref(show(props.modelValue))
const editing = ref(false)

// gia tri doi tu ben ngoai (reset dong, nut "Tra du"...) -> cap nhat chu, tru khi dang go
watch(() => props.modelValue, (v) => {
  const cur = parseNumber(text.value, props.kind)
  if (!editing.value || (v == null ? cur !== null : cur !== Number(v))) text.value = show(v)
})

const clamp = (n: number) => {
  let v = Math.max(props.min, n)
  if (props.max !== undefined) v = Math.min(props.max, v)
  return props.kind === 'integer' ? Math.round(v) : +v.toFixed(props.precision)
}

function onInput(raw: string) {
  // chi giu ky tu hop le de khong go nham chu cai
  const allowed = props.kind === 'integer' ? /[^\d.,\s]/g : /[^\d.,]/g
  text.value = raw.replace(allowed, '')
  // so nguyen (tien): tu chen dau cham nghin ngay khi go (1000000 -> 1.000.000), giu con tro sau dung chu so dang go
  if (props.kind === 'integer') {
    const el: HTMLInputElement | undefined = inputRef.value?.input
    const caret = el?.selectionStart ?? raw.length
    const digitsBefore = raw.slice(0, caret).replace(/\D/g, '').length
    const digits = text.value.replace(/\D/g, '')
    text.value = digits ? groupDigits(digits) : ''
    if (el) nextTick(() => {
      let pos = 0
      for (let seen = 0; pos < text.value.length && seen < digitsBefore; pos++) if (/\d/.test(text.value[pos])) seen++
      el.setSelectionRange(pos, pos)
    })
  }
  const n = parseNumber(text.value, props.kind)
  if (n !== null) set(clamp(n))
  else if (text.value.trim() === '') set(props.nullable ? null : clamp(0))
}

function onBlur() {
  editing.value = false
  text.value = show(props.modelValue)
}

function onFocus(e: FocusEvent) {
  editing.value = true
  ;(e.target as HTMLInputElement).select()
}

function stepBy(dir: 1 | -1) {
  if (props.disabled) return
  const v = clamp(Number(props.modelValue ?? 0) + dir * props.step)
  set(v)
  text.value = show(v)
}

const cls = computed(() => ['number-field', `align-${props.align}`])

defineExpose({ focus: () => inputRef.value?.focus(), select: () => inputRef.value?.select() })
</script>

<template>
  <el-input
    :id="id" ref="inputRef" :key="suffix ? `s:${suffix}` : 'plain'" :class="cls" :model-value="text" :placeholder="placeholder" :disabled="disabled"
    :inputmode="kind === 'integer' ? 'numeric' : 'decimal'" autocomplete="off"
    @update:model-value="onInput" @focus="onFocus" @blur="onBlur"
    @keydown.up.prevent="stepBy(1)" @keydown.down.prevent="stepBy(-1)"
  >
    <!-- key theo suffix: el-input chi xet co slot suffix luc tao -> doi suffix (chon san pham) thi tao lai o -->
    <template v-if="suffix" #suffix><span class="nf-suffix">{{ suffix }}</span></template>
  </el-input>
</template>

<style scoped>
.number-field :deep(input) { font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
.align-right :deep(input) { text-align: right; }
.nf-suffix { font-size: 12px; color: var(--steel-500, #64748b); white-space: nowrap; }
</style>
