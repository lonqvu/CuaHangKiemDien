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

/** Giong LineCalculator o backend, chi de xem truoc. Server moi la noi tinh that. */
export function previewLine(quantity: number, sheets: number, factor: number, unitPrice: number) {
  const f = sheets > 0 && !factor ? 1.08 : factor || 0
  const total = sheets === 0 && f === 0 ? quantity : quantity * sheets * f
  return { factor: f, totalQuantity: total, lineTotal: Math.round(total * unitPrice) }
}

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
