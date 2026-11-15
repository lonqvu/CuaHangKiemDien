<script setup lang="ts">
import type { Customer, ReceiptRow } from '../api/types'
import { num } from '../format'
import { SHOP } from '../shop-info'

// Ban in "Bien lai thanh toan cong no" theo ThanhToanNoReport.jrxml cu
defineProps<{ data: { customer: Customer; currentDebt: number; rows: ReceiptRow[] } | null }>()
const today = new Date().toLocaleDateString('vi-VN')
</script>

<template>
  <Teleport to="body">
    <div v-if="data" class="print-root">
      <div class="inv">
        <header class="shop">
          <b>{{ SHOP.debtReceipt.name }}</b>
          <div v-for="l in SHOP.debtReceipt.lines" :key="l">{{ l }}</div>
        </header>
        <h2>BIÊN LAI THANH TOÁN CÔNG NỢ</h2>
        <table class="info">
          <tr><td>Tên khách hàng:</td><td>{{ data.customer.name }}</td><td class="r">{{ today }}</td></tr>
          <tr><td>Địa chỉ:</td><td colspan="2">{{ data.customer.address }}</td></tr>
          <tr><td>Số điện thoại:</td><td colspan="2">{{ data.customer.phone }}</td></tr>
        </table>
        <table class="items">
          <thead><tr><th>Ngày thanh toán</th><th>Nợ cũ</th><th>Số tiền thanh toán</th><th>Số tiền còn nợ</th></tr></thead>
          <tbody>
            <tr v-for="(r, i) in data.rows" :key="i" class="g">
              <td>{{ new Date(r.date).toLocaleString('vi-VN') }}</td>
              <td class="r">{{ num(r.oldDebt) }}</td><td class="r">{{ num(r.amount) }}</td><td class="r">{{ num(r.remainingDebt) }}</td>
            </tr>
          </tbody>
        </table>
        <p class="cur"><b>Tiền nợ hiện tại: {{ num(data.currentDebt) }}</b></p>
        <div class="signs"><div>Người thu tiền<br /><i>(Ký, ghi rõ họ tên)</i></div></div>
        <p class="notes">{{ SHOP.debtReceipt.footnote }}</p>
      </div>
    </div>
  </Teleport>
</template>

