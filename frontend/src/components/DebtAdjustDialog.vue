<script setup lang="ts">
/*
 * Dieu chinh so no (o "No" man Customers cu, nut "Cap nhat" no cu o PayDialog cu).
 * Bat buoc ly do: tren phieu thu / sao ke hien thanh dong "Dieu chinh: <ly do>", khach doc duoc vi sao no doi.
 */
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import type { Customer } from '../api/types'
import { money } from '../format'
import NumberField from './NumberField.vue'

const props = defineProps<{ customer: Customer | null; modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [Customer] }>()

const form = reactive({ debt: 0, reason: '' })
const saving = ref(false)
const amountField = ref()
const reasons = ['Nợ cũ chuyển từ sổ tay / phần mềm cũ', 'Giảm giá, bớt tiền cho khách', 'Hàng lỗi, khách trả lại', 'Ghi sai số trước đó']

watch(() => props.modelValue, (open) => {
  if (open && props.customer) {
    Object.assign(form, { debt: Number(props.customer.debt), reason: '' })
    nextTick(() => amountField.value?.focus())
  }
})

const diff = computed(() => form.debt - Number(props.customer?.debt ?? 0))

async function save() {
  if (!props.customer) return
  if (diff.value === 0) return ElMessage.warning('Số nợ không đổi')
  if (!form.reason.trim()) return ElMessage.warning('Nhập lý do điều chỉnh (in trên phiếu thu / sao kê)')
  saving.value = true
  try {
    const { data } = await http.post<Customer>(`/customers/${props.customer.id}/debt-adjustments`, { debt: form.debt, note: form.reason.trim() })
    ElMessage.success(`Đã điều chỉnh nợ thành ${money(data.debt)}`)
    emit('saved', data)
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="`Điều chỉnh nợ: ${customer?.name ?? ''}`" width="460px" @update:model-value="emit('update:modelValue', $event)">
    <el-form label-position="top" @submit.prevent="save">
      <p class="now">Nợ hiện tại: <b class="num">{{ money(customer?.debt) }}</b></p>
      <el-form-item label="Số nợ đúng (₫)" for="adj-debt">
        <NumberField id="adj-debt" ref="amountField" v-model="form.debt" kind="integer" :step="100000" />
        <span class="echo">= {{ money(form.debt) }}
          <template v-if="diff !== 0"> ({{ diff > 0 ? 'tăng' : 'giảm' }} {{ money(Math.abs(diff)) }})</template>
        </span>
      </el-form-item>
      <el-form-item label="Lý do (bắt buộc, in trên phiếu thu / sao kê)" for="adj-reason" required>
        <el-input id="adj-reason" v-model="form.reason" maxlength="200" show-word-limit placeholder="VD: Bớt 50.000 vì hàng lỗi" />
        <div class="chips">
          <el-button v-for="r in reasons" :key="r" size="small" @click="form.reason = r">{{ r }}</el-button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">Hủy</el-button>
      <el-button type="primary" :loading="saving" :disabled="diff === 0 || !form.reason.trim()" @click="save">Lưu điều chỉnh</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.now { margin: 0 0 var(--space-md); }
.chips { display: flex; flex-wrap: wrap; gap: var(--space-xs); margin-top: var(--space-sm); }
.chips .el-button + .el-button { margin-left: 0; }
</style>
