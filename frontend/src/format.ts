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

/**
 * Doc so nguoi dung go vao o nhap.
 * - integer (tien, so tam): bo moi dau ngan cach -> "95.000" = "95,000" = 95000
 * - decimal (so luong, he so): dau phay hay dau cham deu la dau thap phan -> "4,2" = "4.2" = 4.2;
 *   neu co ca hai ("1.234,5") thi dau dung sau cung la dau thap phan, cac dau con lai la ngan cach nghin.
 * Tra ve null khi khong co chu so nao.
 */
export function parseNumber(text: string, kind: 'decimal' | 'integer'): number | null {
  const t = text.replace(/\s/g, '')
  if (kind === 'integer') {
    const d = t.replace(/\D/g, '')
    return d ? Number(d) : null
  }
  if (!/\d/.test(t)) return null
  const last = Math.max(t.lastIndexOf(','), t.lastIndexOf('.'))
  const int = (last < 0 ? t : t.slice(0, last)).replace(/\D/g, '')
  const frac = last < 0 ? '' : t.slice(last + 1).replace(/\D/g, '')
  const n = Number(`${int || '0'}.${frac || '0'}`)
  return Number.isFinite(n) ? n : null
}

/** Nhom nghin bang dau cham kieu Viet Nam: "1000000" -> "1.000.000" (chi chu so). */
export const groupDigits = (digits: string) => digits.replace(/^0+(?=\d)/, '').replace(/\B(?=(\d{3})+(?!\d))/g, '.')

/**
 * Hien so trong o nhap: so nguyen (tien, so tam) co dau cham ngan cach nghin (1.000.000) cho de doc;
 * so le (so luong, he so) dung dau phay thap phan (4,2), khong ngan cach nghin (tranh doc nham 1.000 thanh 1).
 */
export function formatInput(v: number | string | null | undefined, kind: 'decimal' | 'integer', precision = 3) {
  const n = Number(v ?? 0)
  if (!Number.isFinite(n)) return ''
  if (kind === 'integer') return groupDigits(String(Math.round(Math.abs(n))))
  return String(+n.toFixed(precision)).replace('.', ',')
}

/** Bo dau tieng Viet + chu thuong, giu nguyen do dai tung ky tu (de to dam dung cho). */
export function foldChar(c: string) {
  return c.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').replace(/,/g, '.').toLowerCase()
}

/**
 * Tach ten thanh cac doan de to dam phan khop voi cau tim (khong dau, theo tung tu),
 * vd highlight("Tôn lạnh 0,4 ly", "ton 0,4") -> [{t:'Tôn',m:true},{t:' lạnh ',m:false},{t:'0,4',m:true},{t:' ly',m:false}]
 */
export function highlight(name: string, query: string): { t: string; m: boolean }[] {
  const chars = [...name]
  const folded = chars.map((c) => foldChar(c).charAt(0) || c)
  const hay = folded.join('')
  const mark = new Array(chars.length).fill(false)
  for (const tok of query.split(/\s+/).map((w) => [...w].map(foldChar).join('')).filter(Boolean)) {
    let i = hay.indexOf(tok)
    while (i >= 0) {
      for (let k = i; k < i + tok.length; k++) mark[k] = true
      i = hay.indexOf(tok, i + tok.length)
    }
  }
  const out: { t: string; m: boolean }[] = []
  chars.forEach((c, i) => {
    const last = out[out.length - 1]
    if (last && last.m === mark[i]) last.t += c
    else out.push({ t: c, m: mark[i] })
  })
  return out
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

/**
 * Nhap nhieu doan ton 1 lan: "4,7x2 4,5x2 3" -> [{4.7,2},{4.5,2},{3,1}].
 * Doan cach nhau bang khoang trang, ";" hoac "+"; "x" / "×" / "*" giua so met va so tam; khong co "x" = so met tron (nhu o So luong, khong nhan he so).
 * Loi -> tra ve doan bi loi de bao dung cho, khong them doan nao.
 */
export function parseSegments(text: string): { segments: { quantity: number; sheets: number }[]; error: string } {
  const norm = text.trim().replace(/\s*[x×*]\s*/gi, 'x').replace(/,(\s|$)/g, ' ')
  const parts = norm.split(/[\s;+]+/).filter(Boolean)
  const segments: { quantity: number; sheets: number }[] = []
  for (const p of parts) {
    const m = /^(\d+(?:[.,]\d*)?)(?:x(\d+))?$/i.exec(p) // "4," = 4
    const quantity = m ? Number(m[1].replace(',', '.')) : NaN
    const sheets = m?.[2] ? Number(m[2]) : 0
    if (!m || !(quantity > 0) || (m[2] && !(sheets >= 1)) || sheets > 9999) return { segments: [], error: `Đoạn "${p}" không hợp lệ (vd: 4,7x2)` }
    segments.push({ quantity, sheets })
  }
  if (segments.length > 50) return { segments: [], error: 'Tối đa 50 đoạn mỗi lần' }
  return { segments, error: '' }
}

/** Gom cac dong cung san pham + don gia thanh 1 cum (theo thu tu xuat hien dau tien). */
export function groupItems<T extends { productId: number; unitPrice: number | string; sheets: number; totalQuantity: number | string; lineTotal: number | string }>(items: T[]) {
  const groups: { key: string; first: T; items: T[]; sheets: number; totalQuantity: number; lineTotal: number }[] = []
  const byKey = new Map<string, (typeof groups)[number]>()
  for (const it of items) {
    const key = `${it.productId}:${Number(it.unitPrice)}`
    let g = byKey.get(key)
    if (!g) { g = { key, first: it, items: [], sheets: 0, totalQuantity: 0, lineTotal: 0 }; byKey.set(key, g); groups.push(g) }
    g.items.push(it)
    g.sheets += it.sheets
    g.totalQuantity = roundHalfEven(g.totalQuantity + Number(it.totalQuantity), 3)
    g.lineTotal += Number(it.lineTotal)
  }
  return groups
}

/**
 * Link mo thang khung chat Zalo voi so dien thoai di dong VN (zalo.me/<so>), null neu khong phai so di dong
 * (khach le khong co so, so ban...). "0912 345 678", "+84 912 345 678", "84912345678" -> https://zalo.me/0912345678
 */
export function zaloChatUrl(phone: string | null | undefined): string | null {
  let d = (phone ?? '').replace(/\D/g, '')
  if (d.startsWith('84') && d.length === 11) d = '0' + d.slice(2)
  return /^0[35789]\d{8}$/.test(d) ? `https://zalo.me/${d}` : null
}
