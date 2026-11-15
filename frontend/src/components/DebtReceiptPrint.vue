<script setup lang="ts">
import type { Customer, ReceiptRow } from '../api/types'
import { SHOP } from '../shop-info'
import { jrDateTime, jrInt, jt } from './jasper'

/** Bien lai thanh toan cong no - dung lai bo cuc ThanhToanNoReport.jrxml (trang 595pt, le 20pt). */
defineProps<{ data: { customer: Customer; currentDebt: number; rows: ReceiptRow[] } | null }>()
const now = new Date()
const R = SHOP.debtReceipt
</script>

<template>
  <Teleport to="body">
    <div v-if="data" class="print-root">
      <div class="jr-page" style="width: 555pt; padding: 20pt">
        <!-- title (180) -->
        <div class="jr-band" style="height: 180pt">
          <div class="jr-t" :style="jt(0, 0, 555, 30, { size: 20, bold: true, align: 'center', valign: 'middle' })">{{ R.name }}</div>
          <div v-for="(l, i) in R.lines" :key="l" class="jr-t" :style="jt(0, 30 + i * 20, 555, 20, { size: 14, align: 'center', valign: 'middle' })">{{ l }}</div>
          <div class="jr-t" :style="jt(0, 159, 555, 20, { size: 16, bold: true, align: 'center', valign: 'middle' })">BIÊN LAI THANH TOÁN CÔNG NỢ</div>
        </div>
        <!-- pageHeader (80) -->
        <div class="jr-band" style="height: 80pt">
          <div class="jr-t" :style="jt(0, 10, 120, 20, { size: 12, bold: true, valign: 'middle' })">Tên khách hàng:</div>
          <div class="jr-t" :style="jt(130, 10, 425, 20, { size: 12, valign: 'middle' })">{{ data.customer.name }}</div>
          <div class="jr-t" :style="jt(370, 10, 185, 20, { size: 10, align: 'center', valign: 'middle' })">{{ jrDateTime(now) }}</div>
          <div class="jr-t" :style="jt(0, 30, 120, 20, { size: 12, bold: true, valign: 'middle' })">Địa chỉ:</div>
          <div class="jr-t" :style="jt(130, 30, 425, 20, { size: 12, valign: 'middle' })">{{ data.customer.address }}</div>
          <div class="jr-t" :style="jt(0, 50, 120, 20, { size: 12, bold: true, valign: 'middle' })">Số điện thoại:</div>
          <div class="jr-t" :style="jt(130, 50, 425, 20, { size: 12, valign: 'middle' })">{{ data.customer.phone }}</div>
        </div>
        <!-- columnHeader (20): o co khung 4 canh -->
        <div class="jr-band" style="height: 20pt">
          <div class="jr-t jr-box" :style="jt(0, 0, 140, 20, { size: 12, bold: true, align: 'center', valign: 'middle' })">Ngày thanh toán</div>
          <div class="jr-t jr-box" :style="jt(140, 0, 140, 20, { size: 12, bold: true, align: 'center', valign: 'middle' })">Nợ cũ </div>
          <div class="jr-t jr-box" :style="jt(280, 0, 140, 20, { size: 12, bold: true, align: 'center', valign: 'middle' })">Số tiền thanh toán</div>
          <div class="jr-t jr-box" :style="jt(420, 0, 135, 20, { size: 12, bold: true, align: 'center', valign: 'middle' })">Số tiền còn nợ</div>
        </div>
        <!-- detail (19): khung trai/duoi/phai -->
        <div v-for="(r, i) in data.rows" :key="i" class="jr-band" style="height: 19pt">
          <div class="jr-t jr-box3" :style="jt(0, -1, 140, 20, { size: 12, align: 'center', valign: 'middle' })">{{ jrDateTime(r.date, false) }}</div>
          <div class="jr-t jr-box3" :style="jt(140, -1, 140, 20, { size: 12, align: 'right', valign: 'middle' })">{{ jrInt(r.oldDebt) }}</div>
          <div class="jr-t jr-box3" :style="jt(280, -1, 140, 20, { size: 12, align: 'right', valign: 'middle' })">{{ jrInt(r.amount) }}</div>
          <div class="jr-t jr-box3" :style="jt(420, -1, 135, 20, { size: 12, align: 'right', valign: 'middle' })">{{ jrInt(r.remainingDebt) }}</div>
        </div>
        <!-- summary (150) -->
        <div class="jr-band" style="height: 150pt">
          <div class="jr-t" :style="jt(0, 6, 180, 20, { size: 12, bold: true, valign: 'middle' })">Tiền nợ hiện tại:</div>
          <div class="jr-t" :style="jt(120, 6, 375, 20, { size: 12, bold: true, valign: 'middle' })">{{ jrInt(data.currentDebt) }}</div>
          <div class="jr-t" :style="jt(0, 26, 306, 24, { size: 9, valign: 'middle' })">{{ R.footnote }}</div>
          <div class="jr-t" :style="jt(360, 30, 195, 20, { size: 12, align: 'center' })">Người thu tiền</div>
          <div class="jr-t" :style="jt(360, 50, 195, 20, { size: 12, italic: true, align: 'center' })">(Ký, ghi rõ họ tên)</div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
