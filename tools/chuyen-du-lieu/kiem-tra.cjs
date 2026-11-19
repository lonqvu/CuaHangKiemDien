#!/usr/bin/env node
/*
 * BUOC 3: doi chieu TUNG DONG giua du lieu cu (file JSON tu xuat.cjs) va PostgreSQL sau khi nap.
 * Tu tinh lai ket qua mong doi theo quy tac (README), KHONG dung lai code cua nap.cjs -> bat duoc loi cua buoc nap.
 *
 *   PG_URL=postgres://... node kiem-tra.cjs du-lieu-cu.json [bao-cao.txt]
 * Thoat ma 0 = khop het; 1 = co cho lech (liet ke trong bao cao).
 */
const fs = require('fs')
const pg = require('pg')
pg.types.setTypeParser(1114, (s) => s) // TIMESTAMP -> giu chu, khong doi mui gio
pg.types.setTypeParser(20, (s) => Number(s)) // BIGINT (id) -> so, de so voi ID cu

const [file = 'du-lieu-cu.json', reportFile] = process.argv.slice(2)
const lines = []
const say = (s = '') => { lines.push(s); console.log(s) }

// so -> so nguyen 1/100 (lam tron nua len), doc tu chu
function c(s) {
  if (s == null || String(s).trim() === '') return null
  const [i, f = ''] = String(s).trim().replace('-', '').split('.')
  const v = BigInt(i || '0') * 10000n + BigInt((f + '0000').slice(0, 4))
  const r = (v + 50n) / 100n
  return String(s).trim().startsWith('-') ? -r : r
}
// so luong (3 so le) -> so nguyen 1/1000
function q(s) {
  if (s == null) return null
  const [i, f = ''] = String(s).trim().split('.')
  return BigInt(i || '0') * 1000n + BigInt((f + '000').slice(0, 3))
}
const vnd = (x) => (x == null ? '-' : (Number(x) / 100).toLocaleString('vi-VN'))
const norm = (s) => { const t = (s ?? '').replace(/\s+/g, ' ').trim(); return t === '' ? null : t }
const ms = (s) => (s == null ? null : Date.parse(String(s).replace(' ', 'T') + 'Z'))

;(async () => {
  const d = JSON.parse(fs.readFileSync(file, 'utf8'))
  const db = new pg.Client({ connectionString: process.env.PG_URL })
  await db.connect()
  const all = async (sql) => (await db.query(sql)).rows

  const errors = []
  const err = (group, msg) => errors.push(`[${group}] ${msg}`)
  const eq = (group, what, a, b) => { if (String(a) !== String(b)) err(group, `${what}: cu=${a} moi=${b}`) }

  say('DOI CHIEU DU LIEU PHAN MEM CU -> HE THONG MOI')
  say(`Nguon: ${d.nguon}, xuat luc ${d.xuatLuc}`)
  say('')

  // ---------- Loai hang
  const pCat = await all('SELECT id, name FROM category')
  const catName = new Map(pCat.map((r) => [r.id, r.name]))
  const oldCatName = new Map(d.classify.map((r) => [r.id, norm(r.name)]))
  const distinct = new Set(d.classify.map((r) => (norm(r.name) ?? '').toLowerCase()))
  eq('loai hang', 'so loai (sau khi gop trung ten)', distinct.size, pCat.length)
  const pCatKeys = new Set(pCat.map((r) => r.name.toLowerCase()))
  for (const k of distinct) if (!pCatKeys.has(k)) err('loai hang', `thieu loai "${k}"`)

  // ---------- San pham
  const pProd = new Map((await all('SELECT id, category_id, name, unit, price, active FROM product')).map((r) => [r.id, r]))
  eq('san pham', 'so san pham', d.products.length, pProd.size)
  for (const o of d.products) {
    const n = pProd.get(o.id)
    if (!n) { err('san pham', `thieu ID ${o.id} ${o.name}`); continue }
    eq('san pham', `ID ${o.id} ten`, norm(o.name), n.name)
    eq('san pham', `ID ${o.id} don vi`, norm(o.unit), n.unit)
    eq('san pham', `ID ${o.id} gia`, c(o.price), c(n.price))
    eq('san pham', `ID ${o.id} loai`, (oldCatName.get(o.classifyId) ?? '').toLowerCase(), (catName.get(n.category_id) ?? '').toLowerCase())
    if (!n.active) err('san pham', `ID ${o.id} bi an`)
  }
  const sb = Number((await all('SELECT count(*) n FROM stock_balance'))[0].n)
  eq('san pham', 'so dong ton kho (bat dau 0)', d.products.length, sb)

  // ---------- Khach hang + cong no
  const pCust = new Map((await all('SELECT id, name, address, phone, debt, active FROM customer')).map((r) => [r.id, r]))
  const pLedger = await all('SELECT customer_id, type, amount, balance_after, note FROM debt_ledger ORDER BY id')
  eq('khach hang', 'so khach', d.customers.length, pCust.size)
  let oldDebt = 0n, newDebt = 0n, withDebt = 0
  for (const o of d.customers) {
    const n = pCust.get(o.id)
    if (!n) { err('khach hang', `thieu ID ${o.id} ${o.name}`); continue }
    eq('khach hang', `ID ${o.id} ten`, norm(o.name), n.name)
    eq('khach hang', `ID ${o.id} SDT`, norm(o.phone), n.phone)
    eq('khach hang', `ID ${o.id} dia chi`, norm(o.address), n.address)
    const od = c(o.debt) ?? 0n, nd = c(n.debt)
    oldDebt += od; newDebt += nd
    eq('cong no', `${o.name} (ID ${o.id}) no`, vnd(od), vnd(nd))
    const led = pLedger.filter((l) => l.customer_id === o.id)
    if (od !== 0n) {
      withDebt++
      if (led.length !== 1 || led[0].type !== 'MANUAL' || c(led[0].amount) !== od || c(led[0].balance_after) !== od)
        err('cong no', `${o.name} (ID ${o.id}): so no phai co dung 1 dong so du ${vnd(od)}, dang co ${JSON.stringify(led)}`)
    } else if (led.length) err('cong no', `${o.name} (ID ${o.id}) khong no ma co dong so no`)
  }
  eq('cong no', 'TONG NO', vnd(oldDebt), vnd(newDebt))
  eq('cong no', 'so dong so no', withDebt, pLedger.length)

  // ---------- Hoa don
  const pOrd = new Map((await all(`SELECT id, code, status, customer_id, customer_name, customer_phone, staff_name, items_total,
      shipping_fee, previous_debt, total_payable, paid_amount, debt_after, note, created_at, checked_out_at FROM orders`)).map((r) => [r.code, r]))
  eq('hoa don', 'so hoa don', d.orders.length, pOrd.size)
  const custIds = new Set(d.customers.map((r) => r.id))
  let walkIn = 0
  for (const o of d.orders) {
    const n = pOrd.get(o.id)
    const g = 'hoa don'
    if (!n) { err(g, `thieu ${o.id}`); continue }
    if (n.status !== 'PAID') err(g, `${o.id} trang thai ${n.status}`)
    if (ms(o.date) !== ms(n.created_at) || ms(o.date) !== ms(n.checked_out_at)) err(g, `${o.id} ngay gio: cu=${o.date} moi=${n.created_at}`)
    eq(g, `${o.id} ten khach`, norm(o.customer) ?? 'Khách lẻ', n.customer_name)
    eq(g, `${o.id} SDT`, norm(o.phone), n.customer_phone)
    eq(g, `${o.id} nhan vien`, norm(o.staff), n.staff_name)
    const bill = c(o.totalBill) ?? 0n, ship = c(o.ship) ?? 0n
    eq(g, `${o.id} tien hang`, vnd(bill), vnd(c(n.items_total)))
    eq(g, `${o.id} phi ship`, vnd(ship), vnd(c(n.shipping_fee)))
    const regular = o.customerId != null && custIds.has(o.customerId)
    eq(g, `${o.id} ma khach`, regular ? o.customerId : null, n.customer_id)
    if (regular) {
      // khach quen: dung nhu da in tren hoa don cu
      const prev = c(o.oldDebt) ?? 0n
      const total = c(o.totalOrder) ?? bill + ship + prev
      const after = c(o.debtBack) ?? 0n
      const paid = c(o.pay) ?? total - after
      eq(g, `${o.id} no cu`, vnd(prev), vnd(c(n.previous_debt)))
      eq(g, `${o.id} tong thanh toan`, vnd(total), vnd(c(n.total_payable)))
      eq(g, `${o.id} khach tra`, vnd(paid), vnd(c(n.paid_amount)))
      eq(g, `${o.id} con no`, vnd(after), vnd(c(n.debt_after)))
    } else {
      // khach le: tra du
      walkIn++
      const total = bill + ship, payIn = c(o.pay) ?? 0n
      eq(g, `${o.id} (le) no cu`, '0', vnd(c(n.previous_debt)))
      eq(g, `${o.id} (le) tong`, vnd(total), vnd(c(n.total_payable)))
      eq(g, `${o.id} (le) khach tra`, vnd(payIn > total ? payIn : total), vnd(c(n.paid_amount)))
      eq(g, `${o.id} (le) con no`, '0', vnd(c(n.debt_after)))
    }
    if (!(n.note ?? '').startsWith(norm(o.note) ?? '')) err(g, `${o.id} ghi chu: cu=${o.note} moi=${n.note}`)
  }

  // ---------- Dong hoa don (tung dong, dung thu tu)
  const pItems = await all(`SELECT o.code, i.product_id, i.product_name, i.unit, i.unit_price, i.quantity, i.sheets, i.factor,
      i.total_quantity, i.line_total, i.cost_total FROM order_item i JOIN orders o ON o.id = i.order_id ORDER BY i.id`)
  const byCode = new Map()
  for (const r of pItems) { if (!byCode.has(r.code)) byCode.set(r.code, []); byCode.get(r.code).push(r) }
  const codes = new Set(d.orders.map((o) => o.id))
  const prodIds = new Set(d.products.map((p) => p.id))
  const oldItems = new Map()
  let dropped = 0
  for (const b of d.bills) {
    if (!codes.has(b.orderId)) { dropped++; continue }
    if (!oldItems.has(b.orderId)) oldItems.set(b.orderId, [])
    oldItems.get(b.orderId).push(b)
  }
  eq('dong hoa don', 'so dong', d.bills.length - dropped, pItems.length)
  let sumOld = 0n, sumNew = 0n
  for (const [code, olds] of oldItems) {
    const news = byCode.get(code) ?? []
    if (olds.length !== news.length) { err('dong hoa don', `${code}: cu ${olds.length} dong, moi ${news.length}`); continue }
    olds.forEach((o, k) => {
      const n = news[k], w = `${code} dong ${k + 1}`
      eq('dong hoa don', `${w} san pham`, prodIds.has(o.productId) ? o.productId : null, n.product_id)
      eq('dong hoa don', `${w} ten`, norm(o.productName), n.product_name)
      eq('dong hoa don', `${w} don vi`, norm(o.unit), n.unit)
      eq('dong hoa don', `${w} so luong`, q(o.amount), q(n.quantity))
      eq('dong hoa don', `${w} so tam`, o.soTam ?? 0, n.sheets)
      eq('dong hoa don', `${w} he so`, q(o.heSo ?? '0'), q(n.factor))
      eq('dong hoa don', `${w} tong SL`, q(o.tongSoLuong), q(n.total_quantity))
      eq('dong hoa don', `${w} don gia`, c(o.price), c(n.unit_price))
      eq('dong hoa don', `${w} thanh tien`, c(o.intoMoney), c(n.line_total))
      if (n.cost_total != null) err('dong hoa don', `${w} co gia von (phai trong)`)
      sumOld += c(o.intoMoney); sumNew += c(n.line_total)
    })
  }
  for (const code of byCode.keys()) if (!oldItems.has(code)) err('dong hoa don', `${code} co dong ma ban cu khong co`)
  eq('dong hoa don', 'TONG THANH TIEN', vnd(sumOld), vnd(sumNew))

  // ---------- Doanh thu tung thang (tien hang)
  const monthOld = new Map()
  for (const o of d.orders) { const m = o.date.slice(0, 7); monthOld.set(m, (monthOld.get(m) ?? 0n) + (c(o.totalBill) ?? 0n)) }
  const monthNew = new Map((await all(`SELECT to_char(created_at, 'YYYY-MM') m, sum(items_total)::text s FROM orders GROUP BY 1`)).map((r) => [r.m, c(r.s)]))
  for (const [m, v] of monthOld) eq('doanh thu', `thang ${m}`, vnd(v), vnd(monthNew.get(m)))
  eq('doanh thu', 'so thang', monthOld.size, monthNew.size)

  // ---------- So hoa don tiep theo
  const seq = (await all(`SELECT last_value, is_called FROM order_code_seq`))[0]
  const maxCode = Math.max(d.invoiceCounter, ...d.orders.map((o) => Number(o.id.replace(/\D/g, ''))))
  eq('so hoa don', 'so hoa don tiep theo', maxCode + 1, Number(seq.last_value) + (seq.is_called ? 1 : 0))

  await db.end()

  // ---------- Bao cao
  say('KET QUA')
  say(`  Loai hang    : ${d.classify.length} -> ${pCat.length} (gop ${d.classify.length - pCat.length} loai trung ten)`)
  say(`  San pham     : ${d.products.length} -> ${pProd.size}`)
  say(`  Khach hang   : ${d.customers.length} -> ${pCust.size}, ${withDebt} khach co no`)
  say(`  Tong no      : cu ${vnd(oldDebt)} d | moi ${vnd(newDebt)} d`)
  say(`  Hoa don      : ${d.orders.length} -> ${pOrd.size} (${walkIn} hoa don khach le: tinh la tra du)`)
  say(`  Dong hoa don : ${d.bills.length - dropped} -> ${pItems.length} (bo ${dropped} dong cua hoa don da xoa o phan mem cu)`)
  say(`  Thanh tien   : cu ${vnd(sumOld)} d | moi ${vnd(sumNew)} d`)
  say(`  Doanh thu    : ${monthOld.size} thang, tung thang deu doi chieu`)
  say(`  HD tiep theo : HD${String(maxCode + 1).padStart(8, '0')}`)
  say('')

  // Bat thuong trong du lieu cu (da chuyen nguyen trang, de chu cua hang xem lai)
  const odd = []
  for (const o of d.orders) {
    const bill = c(o.totalBill) ?? 0n
    const lines = (oldItems.get(o.id) ?? []).reduce((a, b) => a + c(b.intoMoney), 0n)
    if (lines !== bill) odd.push(`${o.id} ${o.date.slice(0, 10)}: tien hang tren hoa don ${vnd(bill)} khac tong cac dong ${vnd(lines)} (giu so tren hoa don)`)
    const pay = c(o.pay), total = c(o.totalOrder)
    if (pay != null && total != null && pay > total * 10n && pay > 100000000n) odd.push(`${o.id} ${o.date.slice(0, 10)} ${o.customer}: khach tra ${vnd(pay)} d, tong chi ${vnd(total)} d (co the go nham)`)
  }
  if (odd.length) { say(`BAT THUONG TRONG DU LIEU CU (${odd.length}, da chuyen nguyen trang):`); odd.forEach((s) => say('  - ' + s)); say('') }

  if (errors.length) {
    say(`LECH: ${errors.length} cho`)
    errors.slice(0, 200).forEach((e) => say('  ' + e))
    if (errors.length > 200) say(`  ... va ${errors.length - 200} cho khac`)
  } else say('=> KHOP HET: moi dong da doi chieu dung giua 2 ben.')
  if (reportFile) fs.writeFileSync(reportFile, lines.join('\n') + '\n')
  process.exit(errors.length ? 1 : 0)
})().catch((e) => { console.error('KIEM TRA LOI:', e.message); process.exit(2) })
