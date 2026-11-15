/**
 * Tien ich de dung lai bo cuc JasperReports (.jrxml) cua app cu tren trinh duyet:
 * moi phan tu dat tuyet doi theo pt (x, y, width, height) giong reportElement.
 * Dinh dang so theo locale may cu (en-US: "1,234,567" - app cu bo dau "," khi doc so tien).
 */
export interface JtOpts {
  size?: number // pt, mac dinh Jasper 10
  bold?: boolean
  italic?: boolean
  align?: 'left' | 'center' | 'right'
  valign?: 'top' | 'middle' | 'bottom'
  stretch?: boolean // isStretchWithOverflow: cho phep tran ra ngoai thay vi cat
}

export function jt(x: number, y: number, w: number, h: number, o: JtOpts = {}) {
  return {
    left: `${x}pt`, top: `${y}pt`, width: `${w}pt`, height: `${h}pt`,
    fontSize: `${o.size ?? 10}pt`,
    fontWeight: o.bold ? '700' : '400',
    fontStyle: o.italic ? 'italic' : 'normal',
    textAlign: o.align ?? 'left',
    justifyContent: o.valign === 'middle' ? 'center' : o.valign === 'bottom' ? 'flex-end' : 'flex-start',
    overflow: 'visible',
    // Jasper cat dong chu khong vua o, nhung khong cat dau tieng Viet nho len tren (KIỂM, SẮT, CỬA):
    // chi cat phan tran o day/hai ben, cho phep tran len tren 0.5em
    clipPath: o.stretch ? 'none' : 'inset(-0.5em -0.15em 0 -0.15em)',
  } as Record<string, string>
}

const en2 = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 })
const en0 = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 })
/** pattern="#,##0.##" */
export const jrMoney = (v: number | string | null | undefined) => (v === null || v === undefined ? '' : en2.format(Number(v)))
/** pattern="#,##0" */
export const jrInt = (v: number | string | null | undefined) => (v === null || v === undefined ? '' : en0.format(Number(v)))
/** BigDecimal.setScale(3, HALF_UP).stripTrailingZeros().toPlainString() */
export const jrPlain = (v: number | string | null | undefined) => String(+Number(v ?? 0).toFixed(3))

const p2 = (n: number) => String(n).padStart(2, '0')
/** pattern="dd/MM/yyyy HH:mm:ss" (withSeconds) hoac "dd/MM/yyyy HH:mm" */
export function jrDateTime(s: string | Date | null | undefined, withSeconds = true) {
  if (!s) return ''
  const d = new Date(s)
  const base = `${p2(d.getDate())}/${p2(d.getMonth() + 1)}/${d.getFullYear()} ${p2(d.getHours())}:${p2(d.getMinutes())}`
  return withSeconds ? `${base}:${p2(d.getSeconds())}` : base
}
