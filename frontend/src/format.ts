const nf = new Intl.NumberFormat('vi-VN')

/** 1.250.000 (khong kem don vi, de can cot) */
export const num = (v: number | string | null | undefined) => nf.format(Number(v ?? 0))
/** 1.250.000 ₫ */
export const money = (v: number | string | null | undefined) => `${num(v)} ₫`
export const qty = (v: number | string | null | undefined) =>
  new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(Number(v ?? 0))
export const dt = (s: string | null | undefined) =>
  s ? new Date(s).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' }) : ''
/** 16/11/26 12:35 - gon cho cot bang; ngay truoc gio (vi-VN mac dinh dat gio truoc). Chi tiet day du dung dt(). */
export const dtShort = (s: string | null | undefined) => {
  if (!s) return ''
  const d = new Date(s)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${p(d.getFullYear() % 100)} ${p(d.getHours())}:${p(d.getMinutes())}`
}
/** Nhan nghiep vu so kho - dung chung the kho. */
export const movementLabel: Record<string, string> = {
  RECEIPT: 'Nhập hàng', RECEIPT_CANCEL: 'Hủy phiếu nhập', SALE: 'Bán hàng', SALE_CANCEL: 'Hủy hóa đơn', COUNT: 'Kiểm kê', ADJUST: 'Điều chỉnh',
}
/** Duong dan toi chung tu goc cua 1 dong so kho. */
export const movementLink = (refType: string, refId: number | null) =>
  !refId ? null : refType === 'ORDER' ? `/orders/${refId}` : refType === 'RECEIPT' ? `/receipts/${refId}` : refType === 'COUNT' ? `/stock-counts/${refId}` : null

/** Noi dung tin nhan Zalo gui kem anh hoa don. */
export function orderMessage(o: { code: string | null; checkedOutAt: string | null; customerName: string | null; itemsTotal: number; shippingFee?: number;
  previousDebt?: number; totalPayable: number; paidAmount: number; debtAfter: number }) {
  const lines = [`Hóa đơn ${o.code ?? ''}${o.checkedOutAt ? ` ngày ${new Date(o.checkedOutAt).toLocaleDateString('vi-VN')}` : ''}`]
  if (o.customerName) lines.push(`Khách hàng: ${o.customerName}`)
  lines.push(`Tiền hàng: ${money(o.itemsTotal)}`)
  if (Number(o.shippingFee)) lines.push(`Phí vận chuyển: ${money(o.shippingFee)}`)
  if (Number(o.previousDebt)) lines.push(`Nợ cũ: ${money(o.previousDebt)}`)
  lines.push(`Tổng thanh toán: ${money(o.totalPayable)}`, `Đã trả: ${money(o.paidAmount)}`)
  if (Number(o.debtAfter) > 0) lines.push(`Còn nợ: ${money(o.debtAfter)}`)
  lines.push('Cảm ơn quý khách!')
  return lines.join('\n')
}

/** Tin nhan Zalo bao cong no (kem anh bien lai neu co). */
export function debtMessage(name: string, debt: number) {
  const today = new Date().toLocaleDateString('vi-VN')
  return Number(debt) > 0
    ? `Kính gửi ${name},\nCông nợ của quý khách tính đến ngày ${today}: ${money(debt)}.\nMong quý khách thu xếp thanh toán. Cảm ơn quý khách!`
    : `Kính gửi ${name},\nTính đến ngày ${today}, quý khách không còn nợ. Cảm ơn quý khách!`
}

export const dateOnly = (s: string | null | undefined) => (s ? new Date(s).toLocaleDateString('vi-VN') : '')

/** Lam tron kieu ngan hang (HALF_EVEN) nhu DecimalFormat cua Java trong app cu. */
export function roundHalfEven(v: number, decimals = 0) {
  const m = 10 ** decimals
  const x = +(v * m).toFixed(8)
  const r = Math.round(x)
  const isHalf = Math.abs(x % 1) === 0.5
  return (isHalf ? (r % 2 === 0 ? r : r - 1) : r) / m
}

/**
 * Giong LineCalculator o backend (va ProductDialog cu), chi de xem truoc. Server moi la noi tinh that.
 * Tong SL hien 3 so le; Thanh tien tinh tren Tong SL chua lam tron, lam tron ve dong.
 */
export function previewLine(quantity: number, sheets: number, factor: number, unitPrice: number) {
  const f = sheets > 0 && !factor ? 1.08 : factor || 0
  const raw = sheets === 0 && f === 0 ? quantity : quantity * sheets * f
  return { factor: f, totalQuantity: roundHalfEven(raw, 3), lineTotal: roundHalfEven(raw * unitPrice) }
}

/** So le kieu BigDecimal.stripTrailingZeros().toPlainString() (dau cham thap phan) nhu tren hoa don cu. */
export const plain = (v: number | string | null | undefined, decimals = 3) =>
  String(+Number(v ?? 0).toFixed(decimals))

export function debounce<T extends (...a: any[]) => void>(fn: T, ms = 250) {
  let t: ReturnType<typeof setTimeout> | undefined
  return (...a: Parameters<T>) => {
    clearTimeout(t)
    t = setTimeout(() => fn(...a), ms)
  }
}

export const isoDate = (d: Date) => {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** Khoang ngay chon nhanh (dung chung man Hoa don, Thong ke). Tra ve [tu, den] dang YYYY-MM-DD. */
export function datePresets(now = new Date()) {
  const d = (y: number, m: number, day: number) => isoDate(new Date(y, m, day))
  const y = now.getFullYear(), m = now.getMonth(), t = now.getDate()
  return [
    { key: 'today', label: 'Hôm nay', from: d(y, m, t), to: d(y, m, t) },
    { key: '7d', label: '7 ngày', from: d(y, m, t - 6), to: d(y, m, t) },
    { key: 'month', label: 'Tháng này', from: d(y, m, 1), to: d(y, m, t) },
    { key: 'lastMonth', label: 'Tháng trước', from: d(y, m - 1, 1), to: d(y, m, 0) },
    { key: 'year', label: 'Năm nay', from: d(y, 0, 1), to: d(y, m, t) },
  ]
}
