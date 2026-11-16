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

/** Hien so trong o nhap: so le dung dau phay (4,2), khong co dau ngan cach nghin. */
export function formatInput(v: number | string | null | undefined, kind: 'decimal' | 'integer', precision = 3) {
  const n = Number(v ?? 0)
  if (!Number.isFinite(n)) return ''
  if (kind === 'integer') return String(Math.round(n))
  return String(+n.toFixed(precision)).replace('.', ',')
}

/** Bo dau tieng Viet + chu thuong, giu nguyen do dai tung ky tu (de to dam dung cho). */
export function foldChar(c: string) {
  return c.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase()
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
