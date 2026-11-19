/*
 * Quy tac chuyen du lieu cu -> moi (dung chung cho nap.cjs; kiem-tra.cjs TU TINH LAI theo mo ta, khong goi file nay).
 *
 *  Loai hang : gop loai trung ten (bo khoang trang, khong phan biet hoa thuong) -> giu ID nho nhat.
 *  San pham  : giu ID; gia (chu) -> so; ten / don vi bo khoang trang thua.
 *  Khach     : giu ID; no hien tai = Customer.Debt (so dung, app cu cho sua tay no nen lich su khong cong lai duoc)
 *              -> moi khach co no: 1 dong so no MANUAL "So du chuyen tu phan mem cu".
 *  Hoa don   : giu ma HDxxxxxxxx + ngay gio, trang thai PAID, KHONG ghi so no (no da chot o tren).
 *     - khach quen: giu nguyen so tien nhu da in (no cu, tong thanh toan, khach tra, con no).
 *       Khach tra trong (1 HD rat cu) -> tong thanh toan - con no.
 *     - khach le  : coi nhu tra du (he thong moi bat khach le tra du): no cu 0, tong = tien hang + ship,
 *       khach tra = max(so da nhap, tong), con no 0. HD le co ghi no cu (khach da bi xoa) -> ghi lai vao ghi chu.
 *  Dong HD   : bo dong cua hoa don da xoa; san pham da xoa -> product_id trong, giu ten.
 *  Bo qua    : lich su tra no (PaymentHistory), tai khoan (Accounts), Unit, Role.
 *  So HD tiep: order_code_seq = InvoiceCounter -> hoa don moi tiep tuc so cu.
 */

/** So dang chu -> so nguyen don vi 1/10000 (BigInt), khong qua so thuc. */
function u(s) {
  if (s == null || String(s).trim() === '') return null
  const m = String(s).trim().match(/^(-?)(\d*)(?:\.(\d*))?$/)
  if (!m) throw new Error('Khong phai so: ' + s)
  const frac = ((m[3] || '') + '0000').slice(0, 4)
  const v = BigInt((m[2] || '0') + frac)
  return m[1] ? -v : v
}
/** 1/10000 -> chu, lam tron 2 so le (nua len, nhu NUMERIC cua PostgreSQL). */
function money(v) {
  if (v == null) return null
  const neg = v < 0n
  let a = neg ? -v : v
  a = (a + 50n) / 100n // lam tron ve 1/100
  const s = a.toString().padStart(3, '0')
  return (neg && a !== 0n ? '-' : '') + s.slice(0, -2) + '.' + s.slice(-2)
}
const max = (a, b) => (a > b ? a : b)
const clean = (s) => { const t = (s ?? '').replace(/\s+/g, ' ').trim(); return t === '' ? null : t }
const catKey = (s) => (clean(s) ?? '').toLowerCase()

const OPENING_NOTE = 'Số dư chuyển từ phần mềm cũ'

module.exports = { u, money, max, clean, catKey, OPENING_NOTE }
