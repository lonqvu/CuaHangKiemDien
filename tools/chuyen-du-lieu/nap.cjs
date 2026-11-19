#!/usr/bin/env node
/*
 * BUOC 2: nap file JSON (xuat.cjs) vao PostgreSQL cua he thong moi. Quy tac: quy-tac.cjs.
 * Chi chay tren database MOI (backend da chay 1 lan de tao bang, chua co khach / san pham / hoa don);
 * toan bo trong 1 transaction: loi giua chung -> khong ghi gi.
 *
 *   PG_URL=postgres://shop:...@localhost:5432/shop node nap.cjs du-lieu-cu.json
 */
const fs = require('fs')
const { Client } = require('pg')
const { u, money, max, clean, catKey, OPENING_NOTE } = require('./quy-tac.cjs')

const file = process.argv[2] || 'du-lieu-cu.json'
const NOW = new Date(Date.now() + 7 * 3600e3).toISOString().replace('T', ' ').slice(0, 23) // gio VN

async function insertMany(db, table, cols, rows) {
  const per = Math.floor(30000 / cols.length)
  for (let i = 0; i < rows.length; i += per) {
    const chunk = rows.slice(i, i + per)
    const params = []
    const values = chunk.map((r) => '(' + cols.map((c) => { params.push(r[c]); return '$' + params.length }).join(',') + ')')
    await db.query(`INSERT INTO ${table} (${cols.join(',')}) VALUES ${values.join(',')}`, params)
  }
}

;(async () => {
  const d = JSON.parse(fs.readFileSync(file, 'utf8'))
  const db = new Client({ connectionString: process.env.PG_URL })
  await db.connect()

  // --- Dieu kien: database moi, da co bang (Flyway >= 8), chua co du lieu ban hang
  const fly = await db.query(`SELECT max(version::int) v FROM flyway_schema_history WHERE success`).catch(() => null)
  if (!fly || !(fly.rows[0].v >= 8)) throw new Error('Database chua co bang cua he thong moi: chay backend 1 lan truoc')
  for (const t of ['category', 'product', 'customer', 'orders', 'debt_ledger', 'supplier', 'goods_receipt', 'stock_movement', 'stock_count']) {
    const n = Number((await db.query(`SELECT count(*) n FROM ${t}`)).rows[0].n)
    if (n > 0) throw new Error(`Bang ${t} da co ${n} dong: chi nap vao database moi (khong ghi de du lieu dang dung)`)
  }

  // --- Loai hang: gop trung ten
  const catOf = new Map() // id cu -> id giu lai
  const cats = []
  const byKey = new Map()
  for (const c of [...d.classify].sort((a, b) => a.id - b.id)) {
    const k = catKey(c.name)
    if (!byKey.has(k)) { byKey.set(k, c.id); cats.push({ id: c.id, name: clean(c.name) }) }
    catOf.set(c.id, byKey.get(k))
  }

  const products = d.products.map((p) => ({
    id: p.id, category_id: catOf.get(p.classifyId) ?? null, name: clean(p.name), unit: clean(p.unit),
    price: money(u(p.price)), active: true, min_stock: '0', created_at: p.createdAt ?? NOW,
  }))
  const productIds = new Set(products.map((p) => p.id))

  const customers = d.customers.map((c) => ({
    id: c.id, name: clean(c.name), address: clean(c.address), phone: clean(c.phone),
    debt: money(u(c.debt) ?? 0n), active: true, version: 0, created_at: c.createdAt ?? NOW,
  }))
  const customerIds = new Set(customers.map((c) => c.id))

  const orderId = new Map() // ma HD cu -> id moi
  const orders = d.orders.map((o, i) => {
    const id = i + 1
    orderId.set(o.id, id)
    const bill = u(o.totalBill) ?? 0n, ship = u(o.ship) ?? 0n
    let prev, total, paid, after, note = clean(o.note)
    if (o.customerId != null && customerIds.has(o.customerId)) {
      prev = u(o.oldDebt) ?? 0n
      total = u(o.totalOrder) ?? bill + ship + prev
      after = u(o.debtBack) ?? 0n
      paid = u(o.pay) ?? total - after
    } else {
      prev = 0n
      total = bill + ship
      paid = max(u(o.pay) ?? 0n, total)
      after = 0n
      const old = u(o.oldDebt) ?? 0n
      if (old !== 0n) note = [note, `Phần mềm cũ ghi nợ cũ ${money(old).replace(/\.00$/, '')} đ`].filter(Boolean).join('. ')
    }
    return {
      id, code: o.id, status: 'PAID', customer_id: o.customerId != null && customerIds.has(o.customerId) ? o.customerId : null,
      customer_name: clean(o.customer) ?? 'Khách lẻ', customer_address: clean(o.address), customer_phone: clean(o.phone),
      staff_name: clean(o.staff), items_total: money(bill), shipping_fee: money(ship), previous_debt: money(prev),
      total_payable: money(total), paid_amount: money(paid), debt_after: money(after), note,
      created_at: o.date, checked_out_at: o.date, refund_amount: '0.00',
    }
  })

  const items = d.bills.filter((b) => orderId.has(b.orderId)).map((b, i) => ({
    id: i + 1, order_id: orderId.get(b.orderId), product_id: productIds.has(b.productId) ? b.productId : null,
    product_name: clean(b.productName) ?? '(không rõ tên)', unit: clean(b.unit), unit_price: money(u(b.price)),
    quantity: b.amount, sheets: b.soTam ?? 0, factor: b.heSo ?? '0', total_quantity: b.tongSoLuong,
    line_total: money(u(b.intoMoney)),
  }))

  const ledger = customers.filter((c) => u(c.debt) !== 0n).map((c, i) => ({
    id: i + 1, customer_id: c.id, type: 'MANUAL', amount: c.debt, balance_after: c.debt,
    note: OPENING_NOTE, created_by: 'Chuyển dữ liệu', created_at: NOW,
  }))

  const maxCode = Math.max(d.invoiceCounter, ...d.orders.map((o) => Number(String(o.id).replace(/\D/g, '')) || 0))

  await db.query('BEGIN')
  try {
    await insertMany(db, 'category', ['id', 'name'], cats)
    await insertMany(db, 'product', ['id', 'category_id', 'name', 'unit', 'price', 'active', 'min_stock', 'created_at'], products)
    await insertMany(db, 'stock_balance', ['product_id'], products.map((p) => ({ product_id: p.id })))
    await insertMany(db, 'customer', ['id', 'name', 'address', 'phone', 'debt', 'active', 'version', 'created_at'], customers)
    await insertMany(db, 'orders', ['id', 'code', 'status', 'customer_id', 'customer_name', 'customer_address', 'customer_phone',
      'staff_name', 'items_total', 'shipping_fee', 'previous_debt', 'total_payable', 'paid_amount', 'debt_after', 'note',
      'created_at', 'checked_out_at', 'refund_amount'], orders)
    await insertMany(db, 'order_item', ['id', 'order_id', 'product_id', 'product_name', 'unit', 'unit_price', 'quantity', 'sheets',
      'factor', 'total_quantity', 'line_total'], items)
    await insertMany(db, 'debt_ledger', ['id', 'customer_id', 'type', 'amount', 'balance_after', 'note', 'created_by', 'created_at'], ledger)

    // ID tu tang tiep tuc sau so lon nhat; so hoa don tiep tuc so cu
    for (const t of ['category', 'product', 'customer', 'orders', 'order_item', 'debt_ledger'])
      await db.query(`SELECT setval(pg_get_serial_sequence('${t}','id'), GREATEST((SELECT max(id) FROM ${t}), 1))`)
    await db.query(`SELECT setval('order_code_seq', $1)`, [maxCode])
    await db.query('COMMIT')
  } catch (e) {
    await db.query('ROLLBACK')
    throw e
  }
  await db.end()
  console.log(`Da nap: ${cats.length} loai hang (gop ${d.classify.length - cats.length} loai trung), ${products.length} san pham, ` +
    `${customers.length} khach (${ledger.length} khach co no), ${orders.length} hoa don, ${items.length} dong ` +
    `(bo ${d.bills.length - items.length} dong cua hoa don da xoa). Hoa don moi bat dau tu HD${String(maxCode + 1).padStart(8, '0')}`)
})().catch((e) => { console.error('NAP LOI:', e.message); process.exit(1) })
