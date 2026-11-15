<script setup lang="ts">
import { computed } from 'vue'
import type { Order, OrderItem } from '../api/types'
import { num, plain } from '../format'
import { SHOP } from '../shop-info'

/**
 * Ban in hoa don, dung bo cuc va cach gom dong cua Bill.jrxml cu:
 * - dong co so tam: gop cac dong cung san pham + so tam + he so + don gia + don vi -> "(4.5 + 4.6) x 2 tam"
 * - sap xep: ten san pham giam dan, he so, so tam, thu tu nhap
 * - moi san pham 1 nhom: "N tam x he so", tong SL + don vi, don gia, tong thanh tien
 */
const props = defineProps<{ order: Order | null }>()

interface Row { productName: string; unit: string | null; sheets: number; factor: number; price: number; amounts: number[]; sheetsTotal: number; totalQuantity: number; lineTotal: number; firstId: number }

const groups = computed(() => {
  const o = props.order
  if (!o) return []
  const rows: Row[] = []
  const merged = new Map<string, Row>()
  for (const i of o.items as OrderItem[]) {
    const sheets = Number(i.sheets || 0)
    const base = { productName: i.productName, unit: i.unit, sheets, factor: Number(i.factor || 0), price: Number(i.unitPrice) }
    if (sheets !== 0) {
      const key = [i.productId, sheets, base.factor, base.price, i.unit ?? '', i.productName].join('|')
      const r = merged.get(key)
      if (r) {
        r.amounts.push(Number(i.quantity)); r.sheetsTotal += sheets
        r.totalQuantity += Number(i.totalQuantity); r.lineTotal += Number(i.lineTotal); r.firstId = Math.min(r.firstId, i.id)
      } else {
        const n: Row = { ...base, amounts: [Number(i.quantity)], sheetsTotal: sheets, totalQuantity: Number(i.totalQuantity), lineTotal: Number(i.lineTotal), firstId: i.id }
        merged.set(key, n); rows.push(n)
      }
    } else {
      rows.push({ ...base, amounts: [Number(i.quantity)], sheetsTotal: 0, totalQuantity: Number(i.totalQuantity), lineTotal: Number(i.lineTotal), firstId: i.id })
    }
  }
  rows.forEach((r) => r.amounts.sort((a, b) => a - b))
  rows.sort((a, b) => b.productName.localeCompare(a.productName, 'vi') || a.factor - b.factor || a.sheets - b.sheets || a.firstId - b.firstId)

  // nhom theo ten san pham (ProductGroup)
  const out: { name: string; rows: Row[]; sheets: number; factor: number; qty: number; unit: string | null; price: number; total: number }[] = []
  for (const r of rows) {
    let g = out[out.length - 1]
    if (!g || g.name !== r.productName) {
      g = { name: r.productName, rows: [], sheets: 0, factor: 0, qty: 0, unit: r.unit, price: 0, total: 0 }
      out.push(g)
    }
    g.rows.push(r); g.sheets += r.sheetsTotal; g.qty += r.totalQuantity; g.total += r.lineTotal
    g.factor = r.factor; g.price = r.price; g.unit = r.unit // Jasper lay gia tri cua dong cuoi nhom
  }
  return out
})

const date = computed(() => (props.order ? new Date(props.order.createdAt).toLocaleDateString('vi-VN') : ''))
const amountText = (r: Row) => {
  const list = r.amounts.map((a) => plain(a)).join(' + ')
  return `${r.amounts.length > 1 ? `(${list})` : list} x ${r.sheets} tấm`
}
</script>

<template>
  <Teleport to="body">
    <div v-if="order" class="print-root">
      <div class="inv">
        <header class="shop">
          <b>{{ SHOP.invoice.name }}</b>
          <div v-for="l in SHOP.invoice.lines" :key="l">{{ l }}</div>
        </header>
        <h2>HÓA ĐƠN BÁN HÀNG</h2>
        <p class="center">Số hóa đơn: <b>{{ order.code }}</b> - {{ date }}</p>
        <table class="info">
          <tr><td>Khách hàng:</td><td>{{ order.customerName }}</td><td>SĐT:</td><td>{{ order.customerPhone }}</td></tr>
          <tr><td>Địa Chỉ:</td><td>{{ order.customerAddress }}</td><td>Người bán hàng:</td><td>{{ order.staffName }}</td></tr>
        </table>
        <table class="items">
          <thead><tr><th>STT</th><th>Sản Phẩm</th><th>SL</th><th>Đơn giá</th><th>Thành tiền</th></tr></thead>
          <tbody>
            <template v-for="(g, gi) in groups" :key="g.name">
              <tr class="g">
                <td class="c">{{ gi + 1 }}</td>
                <td>{{ g.name }}<span v-if="g.rows[g.rows.length - 1].sheets !== 0" class="sub">&nbsp; {{ g.sheets }} tấm x {{ plain(g.factor) }}</span></td>
                <td class="r">{{ plain(g.qty) }} {{ g.unit }}</td>
                <td class="r">{{ num(g.price) }}</td>
                <td class="r">{{ num(g.total) }}</td>
              </tr>
              <tr v-for="(r, ri) in g.rows.filter((x) => x.sheets !== 0)" :key="ri" class="d">
                <td></td><td colspan="4">{{ amountText(r) }}</td>
              </tr>
            </template>
          </tbody>
        </table>
        <div class="bottom">
          <div class="left">
            <b>Ghi chú</b>
            <p>{{ order.note }}</p>
            <div v-if="SHOP.invoice.qrImage" class="qr"><img :src="SHOP.invoice.qrImage" alt="" /><div>QR chuyển khoản</div></div>
          </div>
          <table class="sum">
            <tr><td>Phí vận chuyển:</td><td class="r">{{ num(order.shippingFee) }}</td></tr>
            <tr><td>Tổng tiền hóa đơn:</td><td class="r">{{ num(order.itemsTotal) }}</td></tr>
            <tr><td>Nợ cũ:</td><td class="r">{{ num(order.previousDebt) }}</td></tr>
            <tr class="b"><td>Tổng:</td><td class="r">{{ num(order.totalPayable) }}</td></tr>
            <tr><td>Thanh toán:</td><td class="r">{{ num(order.paidAmount) }}</td></tr>
            <tr class="b"><td>Nợ lại:</td><td class="r">{{ num(order.debtAfter) }}</td></tr>
          </table>
        </div>
        <div class="notes"><b>Lưu ý:</b><div v-for="n in SHOP.invoice.notes" :key="n">{{ n }}</div></div>
      </div>
    </div>
  </Teleport>
</template>

