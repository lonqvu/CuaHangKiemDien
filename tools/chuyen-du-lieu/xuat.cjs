#!/usr/bin/env node
/*
 * BUOC 1: doc database phan mem cu (SQL Server, da khoi phuc tu BackupFile.bak) -> 1 file JSON.
 * So tien / so luong lay dang CHU (khong qua so thuc JS) -> khong sai le dong nao; ngay gio lay nguyen chu (gio VN).
 *
 *   MSSQL_URL="Server=localhost,1433;Database=NhomSatKiemDien;User Id=sa;Password=...;Encrypt=false;TrustServerCertificate=true" \
 *     node xuat.cjs du-lieu-cu.json
 */
const sql = require('mssql')
const fs = require('fs')

const out = process.argv[2] || 'du-lieu-cu.json'
const D = (c) => `CONVERT(varchar(40), ${c})`              // so -> chu, giu nguyen chu so
const T = (c) => `CONVERT(varchar(23), ${c}, 121)`         // ngay -> 'yyyy-mm-dd hh:mi:ss.mmm'

const QUERIES = {
  classify: `SELECT ID AS id, Classify AS name FROM Classify ORDER BY ID`,
  products: `SELECT ID AS id, ClassifyID AS classifyId, Name AS name, RTRIM(Price) AS price, UnitName AS unit,
               ${T('RegistDate')} AS createdAt FROM Products ORDER BY ID`,
  customers: `SELECT ID AS id, CustomerName AS name, Address AS address, PhoneNumber AS phone, ${D('Debt')} AS debt,
                ${T('RegistDate')} AS createdAt FROM Customer ORDER BY ID`,
  orders: `SELECT ID AS id, Customer AS customer, Address AS address, Phone AS phone, ${T('[Date]')} AS date,
             CustomerID AS customerId, ${D('TotalMoneyBill')} AS totalBill, ${D('OldDebt')} AS oldDebt,
             ${D('TotalMoneyOrder')} AS totalOrder, ${D('PayMoney')} AS pay, ${D('DebtBack')} AS debtBack,
             ${D('ShippingFee')} AS ship, StaffName AS staff, Note AS note FROM Orders ORDER BY [Date], ID`,
  bills: `SELECT ID AS id, OrderID AS orderId, ProductID AS productId, ProductName AS productName, UnitName AS unit,
            ${D('Amount')} AS amount, ${D('Price')} AS price, ${D('IntoMoney')} AS intoMoney, ${D('HeSo')} AS heSo,
            SoTam AS soTam, ${D('TongSoLuong')} AS tongSoLuong FROM Bill ORDER BY ID`,
  payments: `SELECT ID AS id, CustomerID AS customerId, ${D('Amount')} AS amount, ${T('PaymentDate')} AS date,
               Deleted AS deleted FROM PaymentHistory ORDER BY ID`,
  invoiceCounter: `SELECT CurrentInvoiceNumber AS n FROM InvoiceCounter`,
}

;(async () => {
  const url = process.env.MSSQL_URL
  if (!url) { console.error('Thieu MSSQL_URL'); process.exit(2) }
  const pool = await sql.connect(url)
  const data = { nguon: 'NhomSatKiemDien (SQL Server)', xuatLuc: new Date().toISOString() }
  for (const [k, q] of Object.entries(QUERIES)) data[k] = (await pool.request().query(q)).recordset
  data.invoiceCounter = data.invoiceCounter[0]?.n ?? 0

  // Tu kiem: dem dong + tong tien tinh ngay tren SQL Server phai bang so trong file
  const chk = (await pool.request().query(`SELECT
      (SELECT COUNT(*) FROM Classify) classify, (SELECT COUNT(*) FROM Products) products,
      (SELECT COUNT(*) FROM Customer) customers, (SELECT COUNT(*) FROM Orders) orders, (SELECT COUNT(*) FROM Bill) bills,
      (SELECT COUNT(*) FROM PaymentHistory) payments,
      ${D('(SELECT SUM(Debt) FROM Customer)')} sumDebt, ${D('(SELECT SUM(IntoMoney) FROM Bill)')} sumBill,
      ${D('(SELECT SUM(TotalMoneyBill) FROM Orders)')} sumOrders`)).recordset[0]
  await pool.close()

  const cents = (s) => (s == null ? 0n : BigInt(Math.round(Number(s) * 100)))
  const sum = (rows, f) => rows.reduce((a, r) => a + cents(r[f]), 0n)
  const bad = []
  for (const k of ['classify', 'products', 'customers', 'orders', 'bills', 'payments'])
    if (chk[k] !== data[k].length) bad.push(`${k}: SQL Server ${chk[k]} dong, file ${data[k].length}`)
  if (cents(chk.sumDebt) !== sum(data.customers, 'debt')) bad.push('tong no khach hang lech')
  if (cents(chk.sumBill) !== sum(data.bills, 'intoMoney')) bad.push('tong thanh tien dong hoa don lech')
  if (cents(chk.sumOrders) !== sum(data.orders, 'totalBill')) bad.push('tong tien hang hoa don lech')
  if (bad.length) { console.error('XUAT LOI:\n' + bad.join('\n')); process.exit(1) }

  fs.writeFileSync(out, JSON.stringify(data))
  console.log(`Da xuat ${out}: ${data.classify.length} loai, ${data.products.length} san pham, ${data.customers.length} khach, ` +
    `${data.orders.length} hoa don, ${data.bills.length} dong, ${data.payments.length} lan tra no; so HD hien tai ${data.invoiceCounter}`)
})().catch((e) => { console.error(e); process.exit(1) })
