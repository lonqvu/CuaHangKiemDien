/*
 * Kiem thu dau-cuoi man Ban hang tren backend THAT (khong mock): tao du lieu mau qua API, thao tac nhu thu ngan
 * (chi ban phim), roi doc lai server de doi chieu tung dong tien voi phep tinh so nguyen chinh xac doc lap.
 *
 * Chay (can Playwright, backend tren :8080 voi DB TRONG, frontend dev tren :5173):
 *   cd backend && DB_URL='jdbc:h2:mem:shop;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE' DB_USER=sa DB_PASSWORD= \
 *     mvn spring-boot:run -Dspring-boot.run.useTestClasspath=true
 *   cd frontend && npm run dev
 *   node scripts/e2e-ban-hang.cjs        # in PASS/FAIL tung muc, exit 1 neu co FAIL
 * Script tao san pham/khach moi moi lan chay -> chay tren DB trong (H2 nhu tren), KHONG chay tren DB that cua cua hang.
 */
const { chromium } = require('playwright');
const API = 'http://localhost:8080/api';
const UI = 'http://localhost:5173';
let token;
const results = [];
function check(name, ok, detail = '') { results.push({ name, ok, detail }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`); }
async function api(method, path, body) {
  const r = await fetch(API + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  if (!r.ok) throw new Error(`${method} ${path} -> ${r.status} ${t}`);
  return t ? JSON.parse(t) : null;
}

// ---- tinh doc lap bang BigInt: SL toi 3 so le, he so toi 4 so le, lam tron HALF_EVEN ----
const scaled = (s, k) => { const [i, f = ''] = String(s).replace(',', '.').split('.'); return BigInt(i + f.padEnd(k, '0').slice(0, k)); };
function halfEvenDiv(n, d) { const q = n / d, r = n % d; if (r * 2n > d) return q + 1n; if (r * 2n < d) return q; return q % 2n === 0n ? q : q + 1n; }
function exactLine(qty, sheets, factor, price) {
  let f = scaled(factor, 4); if (sheets > 0 && f === 0n) f = 10800n;
  const q = scaled(qty, 3);                                  // x1e3
  const raw = sheets === 0 && f === 0n ? q * 10000n : q * BigInt(sheets) * f; // x1e7
  return { totalQty: Number(halfEvenDiv(raw, 10000n)) / 1000, lineTotal: Number(halfEvenDiv(raw * BigInt(price), 10000000n)) };
}
const sum = (a) => a.reduce((x, y) => x + y, 0);

(async () => {
  token = (await api('POST', '/auth/login', { username: 'admin', password: 'admin123' })).token;
  // ---- du lieu mau: ten gan giong nhau nhu danh muc that ----
  const cat = {};
  for (const n of ['Tôn', 'Sắt hộp', 'Nhôm', 'Phụ kiện']) cat[n] = (await api('POST', '/categories', { name: n })).id;
  const P = {};
  for (const [name, c, unit, price] of [
    ['Tôn kẽm 0,4 ly', 'Tôn', 'm', 88000], ['Tôn lạnh 0,4 ly', 'Tôn', 'm', 95000], ['Tôn lạnh 0,45 ly', 'Tôn', 'm', 102000],
    ['Tôn lạnh màu xanh 0,4 ly', 'Tôn', 'm', 99000], ['Carton chống ẩm', 'Phụ kiện', 'tấm', 15000],
    ['Hộp sắt 140x40 dày 1,4', 'Sắt hộp', 'cây', 410000], ['Hộp sắt 40x80 dày 1,4', 'Sắt hộp', 'cây', 285000],
    ['Hộp sắt 40x80 dày 1,45', 'Sắt hộp', 'cây', 296000], ['Nhôm Xingfa hệ 55', 'Nhôm', 'kg', 120000], ['Vít bắn tôn', 'Phụ kiện', 'hộp', 45000],
  ]) P[name] = await api('POST', '/products', { name, categoryId: cat[c], unit, price });
  const tuan = await api('POST', '/customers', { name: 'Anh Tuấn thợ hàn', phone: '0905123456', address: 'Thôn 3', initialDebt: 12450000 });
  const lan = await api('POST', '/customers', { name: 'Chị Lan', phone: '0912000111', address: null, initialDebt: 0 });
  // lam "Tôn lạnh 0,4 ly" thanh hang ban chay (3 hoa don da chot)
  for (let i = 0; i < 3; i++) {
    const o = await api('POST', '/orders');
    await api('POST', `/orders/${o.id}/items`, { productId: P['Tôn lạnh 0,4 ly'].id, quantity: 1, sheets: 0, factor: 0, unitPrice: 95000 });
    await api('POST', `/orders/${o.id}/checkout`, { customerId: null, customerName: 'Khách lẻ', shippingFee: 0, paidAmount: 95000 });
  }

  const browser = await chromium.launch();
  const page = await (await browser.newContext({ viewport: { width: 1366, height: 860 } })).newPage();
  page.on('pageerror', (e) => check('khong co loi JS', false, e.message));
  await page.goto(UI + '/login');
  await page.fill('input[autocomplete=username]', 'admin'); await page.fill('input[type=password]', 'admin123');
  await page.keyboard.press('Enter'); await page.waitForURL(/dashboard/);
  const draftId = () => page.evaluate(() => sessionStorage.getItem('sale.draftId'));
  const draft = async () => api('GET', `/orders/${await draftId()}`);
  const firstOption = () => page.evaluate(() => [...document.querySelectorAll('.el-select-dropdown__item .opt-name')].filter((e) => e.offsetParent)[0]?.innerText.split(' · ')[0] ?? null);
  const focused = () => page.evaluate(() => document.activeElement?.id);
  // go cau tim va CHO phan hoi /api/products cua dung cau do (khong doan thoi gian)
  const searchFor = async (q, focusFirst) => {
    const resp = page.waitForResponse((r) => { const u = new URL(r.url()); return u.pathname === '/api/products' && u.searchParams.get('q') === q; });
    await focusFirst(); await page.keyboard.press('Control+A'); await page.keyboard.type(q);
    await resp; await page.waitForTimeout(150);
  };
  const pick = async (q) => { await searchFor(q, () => page.keyboard.press('F2')); await page.keyboard.press('Enter'); await page.waitForTimeout(250); };
  const typeIn = async (id, v) => { await page.focus('#' + id); await page.keyboard.press('Control+A'); await page.keyboard.type(String(v)); };
  async function addLine(qty, sheets, factor, price) { // dang o o So luong
    await page.keyboard.type(qty); await page.keyboard.press('Tab');
    if (sheets !== undefined) await page.keyboard.type(String(sheets)); await page.keyboard.press('Tab');
    if (factor !== undefined) await page.keyboard.type(factor); await page.keyboard.press('Tab');
    if (price !== undefined) await page.keyboard.type(price);
    await page.keyboard.press('Enter'); await page.waitForTimeout(450);
  }

  // ===== 1. Xep hang ket qua tim (backend that) =====
  await page.goto(UI + '/sale'); await page.waitForTimeout(1200);
  for (const [q, want] of [['ton', 'Tôn lạnh 0,4 ly'], ['ton 0,4', 'Tôn lạnh 0,4 ly'], ['ton lanh 0,45', 'Tôn lạnh 0,45 ly'], ['ton kem', 'Tôn kẽm 0,4 ly'],
    ['hop 40x80 1,4', 'Hộp sắt 40x80 dày 1,4'], ['hop 40 1,4', 'Hộp sắt 40x80 dày 1,4'], ['140', 'Hộp sắt 140x40 dày 1,4'], ['xingfa', 'Nhôm Xingfa hệ 55'], ['nhom', 'Nhôm Xingfa hệ 55'], ['carton', 'Carton chống ẩm']]) {
    await searchFor(q, () => page.focus('#pick-product'));
    const got = await firstOption();
    check(`tim "${q}" -> ket qua dau "${want}"`, got === want, `thuc te: ${got}`);
  }
  await page.keyboard.press('Escape');
  // go nhanh roi Enter ngay khi ket qua chua ve: phai chon dung theo cau vua go, khong lay danh sach cu
  await page.goto(UI + '/sale'); await page.waitForTimeout(1200);
  await searchFor('ton', () => page.focus('#pick-product'));
  await page.keyboard.press('Control+A'); await page.keyboard.type('hop 40x80 1,4'); await page.keyboard.press('Enter'); await page.waitForTimeout(1000);
  check('go nhanh + Enter ngay -> chon dung "Hop sat 40x80 day 1,4", sang So luong', (await page.inputValue('#p')) === '285000' && (await focused()) === 'q');

  // ===== 2. Ban chiu: khach no cu, ton nhieu doan (giu san pham), gia thoa thuan, so le dau phay =====
  await page.goto(UI + '/sale'); await page.waitForTimeout(1200);
  const lines = [];
  await pick('ton lanh 0,4');
  check('Enter chon san pham -> con tro sang So luong', (await focused()) === 'q');
  await addLine('4,7', 2); lines.push(exactLine('4.7', 2, '0', 95000));
  check('them xong: giu san pham, con tro o So luong', (await focused()) === 'q' && (await page.inputValue('#p')) === '95000');
  await addLine('4,5', 2); lines.push(exactLine('4.5', 2, '0', 95000));
  await addLine('3', 1); lines.push(exactLine('3', 1, '0', 95000));
  await pick('hop 40x80 1,4'); await addLine('6', undefined, undefined, '280.000'); lines.push(exactLine('6', 0, '0', 280000));
  await pick('xingfa'); await addLine('2,375'); lines.push(exactLine('2.375', 0, '0', 120000));
  await pick('ton lanh 0,45'); await addLine('5,15', 3, '1,05'); lines.push(exactLine('5.15', 3, '1.05', 102000));
  let o = await draft();
  check('6 dong hang da luu', o.items.length === 6, `${o.items.length} dong`);
  o.items.forEach((it, i) => check(`dong ${i + 1} ${it.productName}: Tong SL ${it.totalQuantity}, tien ${it.lineTotal}`,
    Number(it.totalQuantity) === lines[i]?.totalQty && Number(it.lineTotal) === lines[i]?.lineTotal, `mong doi ${lines[i]?.totalQty} / ${lines[i]?.lineTotal}`));
  const items = sum(lines.map((l) => l.lineTotal));
  check('Tien hang server = tong tu tinh', Number(o.itemsTotal) === items, `${o.itemsTotal} vs ${items}`);
  const uiItems = await page.evaluate(() => document.querySelector('.sum > div dd').innerText.replace(/\./g, ''));
  check('Tien hang tren phieu = server', Number(uiItems) === items, uiItems);
  // bang dong hang tren man hinh khop server
  const uiLineTotals = await page.evaluate(() => [...document.querySelectorAll('.el-table__body tr')].map((tr) => tr.querySelectorAll('td')[5]?.innerText.replace(/\./g, '')));
  check('Thanh tien tung dong tren bang = server', JSON.stringify(uiLineTotals.map(Number)) === JSON.stringify(o.items.map((i) => Number(i.lineTotal))), uiLineTotals.join(','));

  { const r = page.waitForResponse((x) => x.url().includes('/api/customers?q=tuan')); await page.keyboard.press('F4'); await page.keyboard.type('tuan'); await r; await page.waitForTimeout(150); await page.keyboard.press('Enter'); await page.waitForTimeout(300); }
  await typeIn('ship', '150.000'); await typeIn('paid', '3.000.000');
  const payable = items + 150000 + 12450000;
  const uiPayable = await page.evaluate(() => document.querySelector('.total dd').innerText.replace(/\./g, ''));
  check('Tong phai tra tren phieu = tien hang + ship + no cu', Number(uiPayable) === payable, `${uiPayable} vs ${payable}`);
  const note = await page.evaluate(() => document.querySelector('#cta-note')?.innerText);
  check('dong duoi nut Chot noi ro thu / ghi no', note?.includes('3.000.000') && note?.includes('ghi nợ'), note);
  await page.keyboard.press('F9');
  check('F9 dua con tro toi nut Chot (chua chot ngay)', await page.evaluate(() => document.activeElement?.classList.contains('cta')) && (await draft()).status === 'DRAFT');
  const id2 = await draftId();
  await page.keyboard.press('Enter'); await page.waitForTimeout(800);
  o = await api('GET', `/orders/${id2}`);
  check('Enter tai nut Chot -> hoa don PAID', o.status === 'PAID');
  check('No cu tren hoa don', Number(o.previousDebt) === 12450000, o.previousDebt);
  check('Tong phai tra server', Number(o.totalPayable) === payable, `${o.totalPayable} vs ${payable}`);
  check('Con no = tong phai tra - khach tra', Number(o.debtAfter) === payable - 3000000, `${o.debtAfter}`);
  const tuanNow = (await api('GET', '/customers?q=tuan'))[0];
  check('No khach = con no hoa don', Number(tuanNow.debt) === payable - 3000000, `${tuanNow.debt}`);
  const ledger = await api('GET', `/customers/${tuan.id}/debt-ledger`);
  check('So no: tong = no hien tai', sum(ledger.map((l) => Number(l.amount))) === Number(tuanNow.debt));

  // ===== 3. Bay nhap lieu =====
  await page.click('.done-actions >> text=Hóa đơn mới'); await page.waitForTimeout(800);
  await pick('ton lanh 0,4');
  await page.keyboard.type('5'); await page.keyboard.press('Tab'); await page.keyboard.type('2');
  check('go so tam -> he so tu 1,08', (await page.inputValue('#k')) === '1,08');
  await page.keyboard.press('Backspace'); await page.waitForTimeout(100);
  check('xoa so tam -> he so tu ve 0', (await page.inputValue('#k')) === '0');
  await typeIn('k', '1,08'); await page.waitForTimeout(100);
  const problem = await page.evaluate(() => document.querySelector('.line-problem')?.innerText);
  check('so tam 0 + he so 1,08 -> bao do ngay duoi o', !!problem, problem);
  check('nut Them bi khoa', await page.evaluate(() => document.querySelector('.add').disabled));
  await page.focus('#q'); await page.keyboard.press('Enter'); await page.waitForTimeout(400);
  check('Enter khong them dong 0 d', (await draft()).items.length === 0);
  await typeIn('k', '0'); await page.focus('#q'); await page.keyboard.press('Enter'); await page.waitForTimeout(450);
  await page.keyboard.press('Enter'); await page.waitForTimeout(450); // bam Enter lan 2 ngay sau khi them
  check('Enter 2 lan khong tao dong trung (SL ve 0 sau khi them)', (await draft()).items.length === 1, `${(await draft()).items.length} dong`);
  await typeIn('q', '2'); await typeIn('p', '0'); await page.keyboard.press('Enter'); await page.waitForTimeout(400);
  check('don gia 0 -> hoi xac nhan', await page.isVisible('.el-message-box'));
  await page.click('.el-message-box >> text=Sửa đơn giá'); await page.waitForTimeout(300);
  check('chon "Sua don gia" -> khong them, con tro ve Don gia', (await draft()).items.length === 1 && (await focused()) === 'p');
  await page.keyboard.press('Escape'); await page.waitForTimeout(200);
  check('Esc -> bo san pham, ve o tim', (await focused()) === 'pick-product');
  // so le: go dau cham van dung
  await pick('xingfa'); await addLine('1.5');
  check('go "1.5" (dau cham) = 1,5', Number((await draft()).items[1]?.quantity) === 1.5);

  // ===== 4. Sua dong qua hop thoai: bo so tam -> he so ve 0 =====
  await page.click('.row-actions button >> nth=0'); await page.waitForTimeout(400);
  await typeIn('e-q', '4,5'); await typeIn('e-s', '0'); await page.waitForTimeout(100);
  check('hop thoai sua: xoa so tam -> he so ve 0', (await page.inputValue('#e-k')) === '0');
  await page.click('.el-dialog >> text=Lưu dòng'); await page.waitForTimeout(500);
  const ed = (await draft()).items[0];
  check('dong sau sua = 4,5 x 95000', Number(ed.lineTotal) === exactLine('4.5', 0, '0', 95000).lineTotal, `${ed.lineTotal}`);

  // ===== 5. Khach le: thieu ten bi chan; tra thieu khong vao cong no ai =====
  await page.keyboard.press('F9'); await page.waitForTimeout(200);
  check('F9 khi chua co ten khach le -> nhac, khong chot', (await draft()).status === 'DRAFT' && await page.isVisible('.el-message--warning'));
  await page.fill('#walkin-name', 'Khách vãng lai');
  const d5 = await draft(); const total5 = Number(d5.itemsTotal);
  await typeIn('paid', String(total5 - 100000));
  const lanBefore = Number((await api('GET', '/customers?q=lan'))[0].debt);
  const id5 = await draftId();
  await page.click('.cta'); await page.waitForTimeout(800);
  const o5 = await api('GET', `/orders/${id5}`);
  check('khach le: PAID, khong gan khach, con lai 100.000 tren hoa don', o5.status === 'PAID' && o5.customerId === null && Number(o5.debtAfter) === 100000, `${o5.debtAfter}`);
  check('khach le tra thieu khong lam doi no khach khac', Number((await api('GET', '/customers?q=lan'))[0].debt) === lanBefore);

  // ===== 6. Tra thua: tien thua tra lai, no khong am =====
  await page.click('.done-actions >> text=Hóa đơn mới'); await page.waitForTimeout(800);
  await pick('vit ban ton'); await addLine('3');
  { const r = page.waitForResponse((x) => x.url().includes('/api/customers?q=lan')); await page.keyboard.press('F4'); await page.keyboard.type('lan'); await r; await page.waitForTimeout(150); await page.keyboard.press('Enter'); await page.waitForTimeout(300); }
  await typeIn('paid', '200.000');
  const ctaNote = await page.evaluate(() => document.querySelector('#cta-note')?.innerText);
  check('tra thua: hien "tra lai khach 65.000"', ctaNote?.includes('trả lại khách') && ctaNote?.includes('65.000'), ctaNote);
  const id6 = await draftId();
  await page.click('.cta'); await page.waitForTimeout(800);
  const o6 = await api('GET', `/orders/${id6}`);
  check('tra thua: con no 0, no khach 0', Number(o6.debtAfter) === 0 && Number((await api('GET', '/customers?q=lan'))[0].debt) === 0);

  // ===== 7. Thu no o man Khach hang (go co dau cham) =====
  await page.goto(UI + '/customers'); await page.waitForTimeout(1000);
  await page.click('text=Anh Tuấn thợ hàn'); await page.waitForTimeout(600);
  const debtBefore = Number((await api('GET', '/customers?q=tuan'))[0].debt);
  await page.focus('#amt'); await page.keyboard.type('5.000.000'); await page.keyboard.press('Enter'); await page.waitForTimeout(700);
  check('thu no 5.000.000 (go co dau cham)', Number((await api('GET', '/customers?q=tuan'))[0].debt) === debtBefore - 5000000);

  // ===== 8. Huy hoa don o man Hoa don -> hoan cong no =====
  await page.goto(UI + '/orders'); await page.waitForTimeout(1000);
  const before8 = Number((await api('GET', '/customers?q=tuan'))[0].debt);
  const net = Number(o.debtAfter) - Number(o.previousDebt);
  await page.click(`text=${o.code}`); await page.waitForTimeout(500);
  await page.click('.el-drawer >> text=Hủy hóa đơn'); await page.waitForTimeout(300);
  await page.click('.el-message-box >> button >> text=Hủy hóa đơn'); await page.waitForTimeout(700);
  check('huy hoa don: CANCELLED', (await api('GET', `/orders/${o.id}`)).status === 'CANCELLED');
  check('huy hoa don: no khach giam dung phan hoa don lam tang', Number((await api('GET', '/customers?q=tuan'))[0].debt) === before8 - net, `${before8} - ${net}`);

  await browser.close();
  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} PASS`);
  process.exit(failed.length ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(2); });
