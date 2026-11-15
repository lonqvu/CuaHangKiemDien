<script setup lang="ts">
import { computed } from 'vue'
import type { Order, OrderItem } from '../api/types'
import { SHOP } from '../shop-info'
import { jrDateTime, jrMoney, jrPlain, jt } from './jasper'

/**
 * Hoa don in - dung lai nguyen bo cuc Bill.jrxml cua app cu (toa do, co chu, duong ke tinh bang pt).
 * Trang 595pt, le 15pt. Font DejaVu Sans (dong goi trong /public/fonts).
 * Du lieu sap xep / gop dong giong cau SQL trong Bill.jrxml:
 *  - dong co so tam: gop theo san pham + so tam + he so + don gia + don vi, "Amount_List" = cac so luong tang dan
 *  - ORDER BY ten san pham giam dan, he so, so tam, ID
 *  - nhom theo ten san pham (ProductGroup)
 */
const props = defineProps<{ order: Order | null }>()

interface Rec { productName: string; unit: string | null; sheets: number; factor: number; price: number; amounts: number[]; sheetsTotal: number; totalQuantity: number; lineTotal: number; id: number }

const groups = computed(() => {
  const o = props.order
  if (!o) return []
  const recs: Rec[] = []
  const merged = new Map<string, Rec>()
  for (const i of o.items as OrderItem[]) {
    const sheets = Number(i.sheets || 0)
    const base = { productName: i.productName, unit: i.unit, sheets, factor: Number(i.factor || 0), price: Number(i.unitPrice) }
    if (sheets !== 0) { // TonGop
      const key = [i.productId, sheets, base.factor, base.price, i.unit ?? '', i.productName].join('|')
      const r = merged.get(key)
      if (r) {
        r.amounts.push(Number(i.quantity)); r.sheetsTotal += sheets
        r.totalQuantity += Number(i.totalQuantity); r.lineTotal += Number(i.lineTotal); r.id = Math.min(r.id, i.id)
      } else {
        const n: Rec = { ...base, amounts: [Number(i.quantity)], sheetsTotal: sheets, totalQuantity: Number(i.totalQuantity), lineTotal: Number(i.lineTotal), id: i.id }
        merged.set(key, n); recs.push(n)
      }
    } else { // HangThuong
      recs.push({ ...base, amounts: [Number(i.quantity)], sheetsTotal: 0, totalQuantity: Number(i.totalQuantity), lineTotal: Number(i.lineTotal), id: i.id })
    }
  }
  recs.forEach((r) => r.amounts.sort((a, b) => a - b))
  recs.sort((a, b) => b.productName.localeCompare(a.productName, 'vi') || a.factor - b.factor || a.sheets - b.sheets || a.id - b.id)

  const out: { first: Rec; last: Rec; rows: Rec[]; sheets: number; qty: number; total: number }[] = []
  for (const r of recs) {
    let g = out[out.length - 1]
    if (!g || g.first.productName !== r.productName) { g = { first: r, last: r, rows: [], sheets: 0, qty: 0, total: 0 }; out.push(g) }
    g.rows.push(r); g.last = r
    g.sheets += r.sheetsTotal; g.qty += r.totalQuantity; g.total += r.lineTotal
  }
  return out
})

// Truong co evaluationTime="Group" lay gia tri cuoi nhom; truong "Now" lay dong dau nhom
const sheetText = (g: (typeof groups.value)[number]) => `${g.sheets} tấm x ${jrPlain(g.last.factor)}`
const qtyText = (g: (typeof groups.value)[number]) => `${jrPlain(g.qty)} ${g.last.unit ?? 'null'}`
const amountList = (r: Rec) => {
  const list = r.amounts.map((a) => String(a)).join(' + ')
  return `${r.amounts.length > 1 ? `(${list})` : list} x ${r.sheets} tấm`
}
const colLines = [0, 35, 282, 373, 467, 565]
</script>

<template>
  <Teleport to="body">
    <div v-if="order" class="print-root">
      <div class="jr-page" style="width: 595pt; padding: 15pt">
        <!-- title (130) -->
        <div class="jr-band" style="height: 130pt">
          <img class="jr-img" src="/invoice/logo.jpg" alt="" :style="jt(-4, -15, 115, 105)" style="object-fit: fill" />
          <div class="jr-t" :style="jt(111, 2, 436, 26, { size: 20 })">{{ SHOP.invoice.name }}</div>
          <div class="jr-t" :style="jt(111, 32, 436, 20, { size: 14 })">{{ SHOP.invoice.lines[0] }}</div>
          <div class="jr-t" :style="jt(110, 52, 437, 20, { size: 14 })">{{ SHOP.invoice.lines[1] }}</div>
          <div class="jr-t" :style="jt(110, 71, 437, 20, { size: 14 })">{{ SHOP.invoice.lines[2] }}</div>
          <div class="jr-t" :style="jt(110, 91, 437, 20, { size: 14 })">{{ SHOP.invoice.lines[3] }}</div>
        </div>

        <!-- pageHeader (116) -->
        <div class="jr-band" style="height: 116pt">
          <div class="jr-t" :style="jt(187, 4, 210, 20, { size: 16, bold: true })">HÓA ĐƠN BÁN HÀNG</div>
          <div class="jr-t" :style="jt(140, 24, 62, 20, { valign: 'middle' })">Số hóa đơn:</div>
          <div class="jr-t" :style="jt(201, 24, 81, 20, { valign: 'middle' })">{{ order.code }}</div>
          <div class="jr-t" :style="jt(278, 24, 10, 20, { align: 'center', valign: 'middle' })">-</div>
          <div class="jr-t" :style="jt(289, 24, 138, 20, { valign: 'middle' })">{{ jrDateTime(order.createdAt) }}</div>
          <div class="jr-t" :style="jt(0, 55, 85, 20, { valign: 'middle' })">Người bán hàng:</div>
          <div class="jr-t" :style="jt(85, 55, 480, 20, { valign: 'middle' })">{{ order.staffName }}</div>
          <div class="jr-t" :style="jt(0, 75, 63, 20, { valign: 'middle' })">Khách hàng:</div>
          <div class="jr-t" :style="jt(63, 75, 502, 20, { valign: 'middle' })">{{ order.customerName }}</div>
          <div class="jr-t" :style="jt(0, 95, 24, 20, { valign: 'middle' })">SĐT:</div>
          <div class="jr-t" :style="jt(25, 95, 85, 20, { valign: 'middle' })">{{ order.customerPhone }}</div>
          <div class="jr-t" :style="jt(110, 95, 40, 20, { valign: 'middle' })">Địa Chỉ:</div>
          <div class="jr-t" :style="jt(150, 95, 415, 20, { valign: 'middle' })">{{ order.customerAddress }}</div>
          <div class="jr-h" :style="jt(0, 115, 566, 1)" />
        </div>

        <!-- columnHeader (21) -->
        <div class="jr-band" style="height: 21pt">
          <div v-for="x in colLines" :key="x" class="jr-v" :style="jt(x, 0, 1, 21)" />
          <div class="jr-h" :style="jt(1, 20, 565, 1)" />
          <div class="jr-t" :style="jt(0, 0, 35, 21, { bold: true, align: 'center', valign: 'middle' })">STT</div>
          <div class="jr-t" :style="jt(37, 0, 246, 20, { bold: true, align: 'center', valign: 'middle' })">Sản Phẩm</div>
          <div class="jr-t" :style="jt(284, 0, 90, 20, { bold: true, align: 'center', valign: 'middle' })">SL</div>
          <div class="jr-t" :style="jt(375, 0, 93, 21, { bold: true, align: 'center', valign: 'middle' })">Đơn giá</div>
          <div class="jr-t" :style="jt(469, 0, 97, 20, { bold: true, align: 'center', valign: 'middle' })">Thành tiền</div>
        </div>

        <template v-for="(g, gi) in groups" :key="gi">
          <!-- groupHeader ProductGroup (21) -->
          <div class="jr-band" style="height: 21pt">
            <div v-for="x in colLines" :key="x" class="jr-v" :style="jt(x, -1, 1, 22)" />
            <div class="jr-t" :style="jt(2, 0, 34, 20, { size: 9, align: 'center', valign: 'middle' })">{{ gi + 1 }}</div>
            <div class="jr-t" :style="jt(37, 0, 220, 21, { size: 10, bold: true, valign: 'middle' })">{{ g.first.productName }}</div>
            <div v-if="g.first.sheets !== 0" class="jr-t" :style="jt(191, 0, 90, 20, { size: 8, align: 'right', valign: 'middle', stretch: true })">{{ sheetText(g) }}</div>
            <div class="jr-t" :style="jt(284, 0, 90, 20, { size: 10, align: 'center', valign: 'middle', stretch: true })">{{ qtyText(g) }}</div>
            <div class="jr-t" :style="jt(375, 0, 93, 20, { size: 10, align: 'center', valign: 'middle', stretch: true })">{{ jrMoney(g.first.price) }}</div>
            <div class="jr-t" :style="jt(469, 0, 97, 20, { size: 10, align: 'center', valign: 'middle', stretch: true })">{{ jrMoney(g.total) }}</div>
          </div>
          <!-- detail (21), chi in khi so tam != 0 -->
          <div v-for="(r, ri) in g.rows.filter((x) => x.sheets !== 0)" :key="ri" class="jr-band" style="height: 21pt">
            <div v-for="x in colLines" :key="x" class="jr-v" :style="jt(x, -1, 1, 22)" />
            <div class="jr-t" :style="jt(37, 0, 244, 20, { size: 7, align: 'right', valign: 'middle', stretch: true })">{{ amountList(r) }}</div>
          </div>
        </template>

        <!-- summary (300) -->
        <div class="jr-band" style="height: 300pt">
          <div class="jr-t" :style="jt(2, 7, 135, 20, { size: 10, bold: true })">Ghi chú</div>
          <div class="jr-t" :style="jt(2, 27, 227, 108)">{{ order.note }}</div>
          <img v-if="SHOP.invoice.qrImage" class="jr-img" :src="SHOP.invoice.qrImage" alt="" :style="jt(236, 5, 123, 89)" style="object-fit: contain; object-position: left top" />
          <div class="jr-t" :style="jt(240, 90, 123, 20, { size: 8, bold: true, valign: 'middle' })">QR chuyển khoản</div>

          <div class="jr-t" :style="jt(333, 7, 135, 20, { size: 10, bold: true })">Phí vận chuyển:</div>
          <div class="jr-t" :style="jt(467, 7, 99, 20, { align: 'right' })">{{ jrMoney(order.shippingFee) }}</div>
          <div class="jr-t" :style="jt(333, 26, 135, 20, { size: 10, bold: true })">Tổng tiền hóa đơn:</div>
          <div class="jr-t" :style="jt(467, 26, 99, 20, { align: 'right' })">{{ jrMoney(order.itemsTotal) }}</div>
          <div class="jr-t" :style="jt(333, 45, 94, 20, { size: 10, bold: true })">Nợ cũ:</div>
          <div class="jr-t" :style="jt(467, 45, 99, 20, { align: 'right' })">{{ jrMoney(order.previousDebt) }}</div>
          <div class="jr-t" :style="jt(333, 65, 94, 20, { size: 10, bold: true })">Tổng: </div>
          <div class="jr-t" :style="jt(467, 65, 98, 20, { align: 'right' })">{{ jrMoney(order.totalPayable) }}</div>
          <div class="jr-t" :style="jt(333, 85, 94, 20, { size: 10, bold: true })">Thanh toán:</div>
          <div class="jr-t" :style="jt(467, 85, 98, 20, { align: 'right' })">{{ jrMoney(order.paidAmount) }}</div>
          <div class="jr-t" :style="jt(333, 105, 94, 20, { size: 10, bold: true })">Nợ lại:</div>
          <div class="jr-t" :style="jt(467, 105, 98, 20, { align: 'right' })">{{ jrMoney(order.debtAfter) }}</div>

          <div class="jr-t" :style="jt(0, 133, 135, 20, { size: 9, bold: true, valign: 'middle' })">Lưu ý:</div>
          <div class="jr-t" :style="jt(0, 153, 550, 58, { size: 9 })">{{ SHOP.invoice.notes.join('\n') }}</div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
