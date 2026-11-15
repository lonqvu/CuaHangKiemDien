<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import type { Customer, Order, Product } from '../api/types'
import { money } from '../format'

const order = ref<Order | null>(null)
const products = ref<Product[]>([])
const customers = ref<Customer[]>([])
const customer = ref<Customer | null>(null)
const checkoutForm = reactive({ shippingFee: 0, paidAmount: 0, note: '', walkInName: '' })
const line = reactive({ productId: null as number | null, quantity: 1, sheets: 0, factor: 0, unitPrice: null as number | null })

async function newOrder() {
  const { data } = await http.post<Order>('/orders')
  order.value = data
  customer.value = null
  Object.assign(checkoutForm, { shippingFee: 0, paidAmount: 0, note: '', walkInName: '' })
  Object.assign(line, { productId: null, quantity: 1, sheets: 0, factor: 0, unitPrice: null })
}

async function searchProducts(q: string) {
  products.value = (await http.get<Product[]>('/products', { params: { q } })).data
}
async function searchCustomers(q: string) {
  customers.value = (await http.get<Customer[]>('/customers', { params: { q } })).data
}

function onPickProduct(id: number) {
  line.unitPrice = products.value.find((p) => p.id === id)?.price ?? null
}

async function addItem() {
  if (!order.value || !line.productId) return ElMessage.warning('Chọn sản phẩm')
  const { data } = await http.post<Order>(`/orders/${order.value.id}/items`, {
    productId: line.productId,
    quantity: line.quantity,
    sheets: line.sheets,
    factor: line.factor, // 0 + co so tam -> server tu dung he so 1.08
    unitPrice: line.unitPrice,
  })
  order.value = data
  Object.assign(line, { productId: null, quantity: 1, sheets: 0, factor: 0, unitPrice: null })
}

async function removeItem(itemId: number) {
  order.value = (await http.delete<Order>(`/orders/${order.value!.id}/items/${itemId}`)).data
}

// Chi de hien thi; server moi la noi tinh tien that su khi chot don
const previewPayable = computed(
  () => (order.value?.itemsTotal ?? 0) + Number(checkoutForm.shippingFee || 0) + (customer.value?.debt ?? 0),
)
const previewRemain = computed(() => previewPayable.value - Number(checkoutForm.paidAmount || 0))

async function checkout() {
  if (!order.value) return
  const { data } = await http.post<Order>(`/orders/${order.value.id}/checkout`, {
    customerId: customer.value?.id ?? null,
    customerName: customer.value ? null : checkoutForm.walkInName,
    shippingFee: checkoutForm.shippingFee || 0,
    paidAmount: checkoutForm.paidAmount || 0,
    note: checkoutForm.note,
  })
  ElMessage.success(`Đã chốt ${data.code}. Còn nợ: ${money(data.debtAfter)}`)
  await newOrder()
}

onMounted(async () => {
  await newOrder()
  searchProducts('')
  searchCustomers('')
})

// Roi man hinh khi hoa don nhap con trong thi xoa luon cho sach
onBeforeUnmount(() => {
  if (order.value && order.value.status === 'DRAFT' && order.value.items.length === 0) {
    http.delete(`/orders/${order.value.id}`).catch(() => {})
  }
})
</script>

<template>
  <div v-if="order">
    <h3>Hóa đơn {{ order.code }}</h3>

    <el-form inline>
      <el-form-item label="Sản phẩm">
        <el-select v-model="line.productId" filterable remote :remote-method="searchProducts" placeholder="Tìm sản phẩm"
                   style="width: 260px" @change="onPickProduct">
          <el-option v-for="p in products" :key="p.id" :label="`${p.name} (${money(p.price)}/${p.unit ?? ''})`" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="Số lượng"><el-input-number v-model="line.quantity" :min="0.001" :step="1" /></el-form-item>
      <el-form-item label="Số tấm"><el-input-number v-model="line.sheets" :min="0" /></el-form-item>
      <el-form-item label="Hệ số"><el-input-number v-model="line.factor" :min="0" :step="0.01" :precision="3" /></el-form-item>
      <el-form-item label="Đơn giá"><el-input-number v-model="line.unitPrice" :min="0" :step="1000" /></el-form-item>
      <el-button type="primary" @click="addItem">Thêm</el-button>
    </el-form>

    <el-table :data="order.items" border show-summary :summary-method="() => ['', '', '', '', '', '', money(order!.itemsTotal), '']">
      <el-table-column prop="productName" label="Sản phẩm" />
      <el-table-column prop="unit" label="ĐVT" width="80" />
      <el-table-column prop="quantity" label="SL" width="90" />
      <el-table-column prop="sheets" label="Tấm" width="80" />
      <el-table-column prop="factor" label="Hệ số" width="90" />
      <el-table-column label="Đơn giá" width="130"><template #default="{ row }">{{ money(row.unitPrice) }}</template></el-table-column>
      <el-table-column label="Thành tiền" width="150"><template #default="{ row }">{{ money(row.lineTotal) }}</template></el-table-column>
      <el-table-column width="90"><template #default="{ row }"><el-button link type="danger" @click="removeItem(row.id)">Xóa</el-button></template></el-table-column>
    </el-table>

    <el-card style="margin-top: 16px">
      <el-form label-width="130px" style="max-width: 560px">
        <el-form-item label="Khách hàng">
          <el-select v-model="customer" value-key="id" filterable remote clearable :remote-method="searchCustomers"
                     placeholder="Khách lẻ (để trống)" style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :label="`${c.name} ${c.phone ?? ''} - nợ ${money(c.debt)}`" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!customer" label="Tên khách lẻ"><el-input v-model="checkoutForm.walkInName" /></el-form-item>
        <el-form-item label="Phí ship"><el-input-number v-model="checkoutForm.shippingFee" :min="0" :step="10000" /></el-form-item>
        <el-form-item label="Nợ cũ">{{ money(customer?.debt ?? 0) }}</el-form-item>
        <el-form-item label="Tổng phải trả"><b>{{ money(previewPayable) }}</b></el-form-item>
        <el-form-item label="Khách trả"><el-input-number v-model="checkoutForm.paidAmount" :min="0" :step="10000" /></el-form-item>
        <el-form-item label="Còn lại (ghi nợ)">
          <b :style="{ color: previewRemain > 0 ? '#e6a23c' : 'inherit' }">{{ money(previewRemain) }}</b>
          <span v-if="!customer && previewRemain > 0" style="margin-left: 8px; color: #f56c6c">Khách lẻ phải trả đủ</span>
        </el-form-item>
        <el-form-item label="Ghi chú"><el-input v-model="checkoutForm.note" /></el-form-item>
        <el-form-item>
          <el-button type="success" :disabled="!order.items.length" @click="checkout">Chốt hóa đơn</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
