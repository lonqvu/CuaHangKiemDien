const nf = new Intl.NumberFormat('vi-VN')

/** 1.250.000 (khong kem don vi, de can cot) */
export const num = (v: number | string | null | undefined) => nf.format(Number(v ?? 0))
/** 1.250.000 ₫ */
export const money = (v: number | string | null | undefined) => `${num(v)} ₫`
export const qty = (v: number | string | null | undefined) =>
  new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(Number(v ?? 0))
export const dt = (s: string | null | undefined) =>
  s ? new Date(s).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' }) : ''
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
