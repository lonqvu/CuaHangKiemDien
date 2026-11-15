<script setup lang="ts">
import type { Order } from '../api/types'
import { dt, num, qty } from '../format'

// Thay man "Xuat HD" (JasperReport) cu: in bang trinh duyet, khong can file .jrxml
defineProps<{ order: Order | null }>()
</script>

<template>
  <Teleport to="body">
    <div v-if="order" class="print-root">
      <div class="inv">
        <h2>ĐẠI LÝ NHÔM SẮT KIỂM DIÊN</h2>
        <p class="center">HÓA ĐƠN BÁN HÀNG · <b>{{ order.code }}</b></p>
        <p>Ngày: {{ dt(order.checkedOutAt || order.createdAt) }} &nbsp; Người lập: {{ order.staffName }}</p>
        <p>Khách hàng: {{ order.customerName || 'Khách lẻ' }}<span v-if="order.customerPhone"> · {{ order.customerPhone }}</span></p>
        <p v-if="order.customerAddress">Địa chỉ: {{ order.customerAddress }}</p>
        <table>
          <thead><tr><th>#</th><th>Mặt hàng</th><th>SL</th><th>Tấm</th><th>Hệ số</th><th>Tổng SL</th><th>Đơn giá</th><th>Thành tiền</th></tr></thead>
          <tbody>
            <tr v-for="(i, idx) in order.items" :key="i.id">
              <td>{{ idx + 1 }}</td><td>{{ i.productName }}<span v-if="i.unit"> ({{ i.unit }})</span></td>
              <td class="r">{{ qty(i.quantity) }}</td><td class="r">{{ i.sheets || '' }}</td><td class="r">{{ i.factor ? qty(i.factor) : '' }}</td>
              <td class="r">{{ qty(i.totalQuantity) }}</td><td class="r">{{ num(i.unitPrice) }}</td><td class="r">{{ num(i.lineTotal) }}</td>
            </tr>
          </tbody>
        </table>
        <table class="sum">
          <tr><td>Tiền hàng</td><td class="r">{{ num(order.itemsTotal) }}</td></tr>
          <tr v-if="Number(order.shippingFee)"><td>Phí vận chuyển</td><td class="r">{{ num(order.shippingFee) }}</td></tr>
          <tr v-if="Number(order.previousDebt)"><td>Nợ cũ</td><td class="r">{{ num(order.previousDebt) }}</td></tr>
          <tr class="b"><td>Tổng phải trả</td><td class="r">{{ num(order.totalPayable) }}</td></tr>
          <tr><td>Khách trả</td><td class="r">{{ num(order.paidAmount) }}</td></tr>
          <tr class="b"><td>Còn nợ</td><td class="r">{{ num(order.debtAfter) }}</td></tr>
        </table>
        <p v-if="order.note">Ghi chú: {{ order.note }}</p>
        <div class="sign"><div>Khách hàng<br /><i>(Ký, ghi rõ họ tên)</i></div><div>Người bán<br /><i>(Ký, ghi rõ họ tên)</i></div></div>
      </div>
    </div>
  </Teleport>
</template>

<style>
.print-root .inv { font-family: 'Fira Sans', Arial, sans-serif; font-size: 12pt; color: #000; }
.print-root h2 { text-align: center; font-size: 15pt; margin: 0 0 4px; }
.print-root p { margin: 2px 0; }
.print-root .center { text-align: center; margin-bottom: 8px; }
.print-root table { width: 100%; border-collapse: collapse; margin-top: 8px; }
.print-root th, .print-root td { border: 1px solid #000; padding: 3px 5px; font-size: 11pt; }
.print-root .r { text-align: right; font-variant-numeric: tabular-nums; }
.print-root .sum { width: 50%; margin-left: auto; }
.print-root .sum td { border: none; border-bottom: 1px dotted #000; }
.print-root .sum .b td { font-weight: 700; }
.print-root .sign { display: flex; justify-content: space-around; margin-top: 24px; text-align: center; height: 90px; }
</style>
