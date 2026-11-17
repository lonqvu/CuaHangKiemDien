/*
 * Kiem thu dau-cuoi man Ban hang tren backend THAT (khong mock): tao du lieu mau qua API, thao tac nhu thu ngan
 * (chi ban phim), roi doc lai server de doi chieu tung dong tien voi phep tinh so nguyen chinh xac doc lap.
 *
 * Chay (can Playwright, backend tren :8090 (API_URL de doi) voi DB TRONG, frontend dev tren :5173):
 *   cd backend && DB_URL='jdbc:h2:mem:shop;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE' DB_USER=sa DB_PASSWORD= \
 *     ./mvnw spring-boot:run -Dspring-boot.run.useTestClasspath=true
 *   cd frontend && npm run dev
 *   node scripts/e2e-ban-hang.cjs        # in PASS/FAIL tung muc, exit 1 neu co FAIL
 * Script tao san pham/khach moi moi lan chay -> chay tren DB trong (H2 nhu tren), KHONG chay tren DB that cua cua hang.
 */
const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const API = (process.env.API_URL ?? 'http://localhost:8090') + '/api';
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

// API danh sach co phan trang ({ items, total }): doc het cac trang
async function all(p) {
  const out = [];
  for (let page = 0; ; page++) {
    const r = await api('GET', `${p}${p.includes('?') ? '&' : '?'}page=${page}&size=100`);
    if (Array.isArray(r)) return r;
    out.push(...r.items);
    if (out.length >= r.total || !r.items.length) return out;
  }
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
  check('chon ton (ban theo m) -> con tro o "Nhieu doan"', (await focused()) === 'segs');
  // nhap nhieu doan 1 lan: doan sai -> bao loi, khong them doan nao
  await page.keyboard.type('4,7x2 4,5y2'); await page.keyboard.press('Enter'); await page.waitForTimeout(400);
  check('nhieu doan co doan sai -> khong them dong nao', (await draft()).items.length === 0);
  await page.keyboard.press('Control+A'); await page.keyboard.type('4,7x2 4,5x2 3x1');
  const segPrev = await page.evaluate(() => document.querySelector('.segs-preview')?.innerText);
  await page.keyboard.press('Enter'); await page.waitForTimeout(500);
  lines.push(exactLine('4.7', 2, '0', 95000), exactLine('4.5', 2, '0', 95000), exactLine('3', 1, '0', 95000));
  check('xem truoc nhieu doan: 3 doan, 5 tam, dung tong tien', segPrev?.includes('3 đoạn') && segPrev?.includes('5 tấm') &&
    segPrev?.replace(/\./g, '').includes(String(sum(lines.map((l) => l.lineTotal)))), segPrev);
  check('"4,7x2 4,5x2 3x1" + Enter -> 3 dong', (await draft()).items.length === 3);
  check('them xong: giu san pham, o Nhieu doan trong, con tro o lai', (await focused()) === 'segs' && (await page.inputValue('#segs')) === '' && (await page.inputValue('#p')) === '95000');
  await pick('hop 40x80 1,4'); await addLine('6', undefined, undefined, '280.000'); lines.push(exactLine('6', 0, '0', 280000));
  await pick('xingfa'); await addLine('2,375'); lines.push(exactLine('2.375', 0, '0', 120000));
  await pick('ton lanh 0,45'); await page.keyboard.press('Tab'); // quen tay go tung dong: Tab tu o Nhieu doan -> So luong (khong xuong bang)
  check('Tab tu o Nhieu doan -> sang So luong', (await focused()) === 'q');
  await addLine('5,15', 3, '1,05'); lines.push(exactLine('5.15', 3, '1.05', 102000));
  let o = await draft();
  check('6 dong hang da luu', o.items.length === 6, `${o.items.length} dong`);
  o.items.forEach((it, i) => check(`dong ${i + 1} ${it.productName}: Tong SL ${it.totalQuantity}, tien ${it.lineTotal}`,
    Number(it.totalQuantity) === lines[i]?.totalQty && Number(it.lineTotal) === lines[i]?.lineTotal, `mong doi ${lines[i]?.totalQty} / ${lines[i]?.lineTotal}`));
  const items = sum(lines.map((l) => l.lineTotal));
  check('Tien hang server = tong tu tinh', Number(o.itemsTotal) === items, `${o.itemsTotal} vs ${items}`);
  const uiItems = await page.evaluate(() => document.querySelector('.sum > div dd').innerText.replace(/\./g, ''));
  check('Tien hang tren phieu = server', Number(uiItems) === items, uiItems);
  // bang dong hang tren man hinh khop server
  // bang: 3 doan ton gop 1 cum (5 cum hien), tung doan van co dong rieng (an trong cum) khop server
  const ui = await page.evaluate(() => ({
    top: [...document.querySelectorAll('.el-table__body tr:not(.seg-row)')].map((tr) => tr.querySelector('.line-total')?.innerText.replace(/\./g, '')),
    group: document.querySelector('.group-row')?.innerText,
    items: Object.fromEntries([...document.querySelectorAll('.row-actions[data-item]')].map((d) => [d.dataset.item, d.closest('tr').querySelector('.line-total').innerText.replace(/\./g, '')])),
  }));
  const tonSum = sum(o.items.slice(0, 3).map((i) => Number(i.lineTotal)));
  check('bang gop 3 doan ton thanh 1 cum -> 4 dong tren cung', ui.top.length === 4, ui.top.join(','));
  check('cum ton: tong tien = tong 3 doan, ghi 3 doan + 5 tam', Number(ui.top[0]) === tonSum && ui.group?.includes('3 đoạn: 4,7×2 + 4,5×2 + 3×1') && ui.group?.includes('5 tấm'), ui.group);
  check('Thanh tien tung doan / dong tren bang = server', o.items.every((i) => Number(ui.items[i.id]) === Number(i.lineTotal)), JSON.stringify(ui.items));
  // mo cum -> sua / xoa duoc tung doan
  await page.click('.group-row .el-table__expand-icon'); await page.waitForTimeout(300);
  check('mo cum -> thay 3 doan', (await page.$$eval('.seg-row', (r) => r.filter((x) => x.offsetParent).length)) === 3);

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
  const tuanNow = (await all('/customers?q=tuan'))[0];
  check('No khach = con no hoa don', Number(tuanNow.debt) === payable - 3000000, `${tuanNow.debt}`);
  const ledger = await all(`/customers/${tuan.id}/debt-ledger`);
  check('So no: tong = no hien tai', sum(ledger.map((l) => Number(l.increase ?? 0) - Number(l.decrease ?? 0))) === Number(tuanNow.debt));

  // ===== 3. Bay nhap lieu =====
  await page.click('.done-actions >> text=Hóa đơn mới'); await page.waitForTimeout(800);
  await pick('ton lanh 0,4');
  await page.keyboard.type('3'); await page.keyboard.press('Enter'); await page.waitForTimeout(500); // "3" khong co x = 3 m tron
  { const it = (await draft()).items[0]; check('o Nhieu doan go "3" -> 3 m tron (khong nhan he so)', it && Number(it.totalQuantity) === 3 && it.sheets === 0, JSON.stringify(it)); }
  await page.click('.row-actions button:nth-child(2)'); await page.click('.el-message-box__btns .el-button--primary'); await page.waitForTimeout(400);
  await page.focus('#segs'); await page.keyboard.press('Enter');
  check('o Nhieu doan trong + Enter -> sang So luong', (await focused()) === 'q');
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

  // ===== 5. Khach le: thieu ten bi chan; PHAI TRA DU (khong ghi no) =====
  await page.keyboard.press('F9'); await page.waitForTimeout(200);
  check('F9 khi chua co ten khach le -> nhac, khong chot', (await draft()).status === 'DRAFT' && await page.isVisible('.el-message--warning'));
  await page.fill('#walkin-name', 'Khách vãng lai');
  const d5 = await draft(); const total5 = Number(d5.itemsTotal);
  await typeIn('paid', String(total5 - 100000));
  const lanBefore = Number((await all('/customers?q=lan'))[0].debt);
  const id5 = await draftId();
  const reason5 = await page.evaluate(() => document.querySelector('#block-reason')?.innerText);
  check('khach le tra thieu: nut Chot khoa, bao con thieu 100.000 + goi y chon khach', await page.evaluate(() => document.querySelector('.cta').disabled)
    && reason5?.includes('còn thiếu 100.000') && reason5?.includes('chọn hoặc tạo khách'), reason5);
  check('khach le tra thieu: dong "Con thieu" thay "Con lai"', await page.evaluate(() => document.querySelector('.remain dt')?.innerText) === 'Còn thiếu');
  check('khach le tra thieu: server cung tu choi', (await fetch(API + `/orders/${id5}/checkout`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
    body: JSON.stringify({ customerName: 'Khách vãng lai', shippingFee: 0, paidAmount: total5 - 1 }) })).status === 400 && (await draft()).status === 'DRAFT');
  await page.click('.quick >> text=Trả đủ'); await page.waitForTimeout(150);
  await page.click('.cta'); await page.waitForTimeout(800);
  const o5 = await api('GET', `/orders/${id5}`);
  check('khach le tra du: PAID, khong gan khach, con lai 0', o5.status === 'PAID' && o5.customerId === null && Number(o5.debtAfter) === 0 && Number(o5.paidAmount) === total5, `${o5.debtAfter}`);
  check('khach le khong lam doi no khach khac', Number((await all('/customers?q=lan'))[0].debt) === lanBefore);

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
  check('tra thua: con no 0, no khach 0', Number(o6.debtAfter) === 0 && Number((await all('/customers?q=lan'))[0].debt) === 0);

  // ===== 7. Thu no o man Khach hang (go co dau cham) =====
  await page.goto(UI + '/customers'); await page.waitForTimeout(1000);
  await page.click('text=Anh Tuấn thợ hàn'); await page.waitForTimeout(600);
  const debtBefore = Number((await all('/customers?q=tuan'))[0].debt);
  await page.focus('#amt'); await page.keyboard.type('5.000.000'); await page.keyboard.press('Enter'); await page.waitForTimeout(700);
  check('thu no 5.000.000 (go co dau cham)', Number((await all('/customers?q=tuan'))[0].debt) === debtBefore - 5000000);

  // ===== 8. Huy hoa don o man Hoa don -> hoan cong no =====
  await page.goto(UI + '/orders'); await page.waitForTimeout(1000);
  const before8 = Number((await all('/customers?q=tuan'))[0].debt);
  const net = Number(o.debtAfter) - Number(o.previousDebt);
  await page.click(`text=${o.code}`); await page.waitForTimeout(500);
  await page.click('.el-drawer >> text=Hủy hóa đơn'); await page.waitForTimeout(300);
  await page.click('.el-message-box >> button >> text=Hủy hóa đơn'); await page.waitForTimeout(700);
  check('huy hoa don: CANCELLED', (await api('GET', `/orders/${o.id}`)).status === 'CANCELLED');
  check('huy hoa don: no khach giam dung phan hoa don lam tang', Number((await all('/customers?q=tuan'))[0].debt) === before8 - net, `${before8} - ${net}`);

  // ===== 9. Huy hoa don sau khi khach da tra het no -> no ve 0, bao tra lai tien mat =====
  const khach9 = await api('POST', '/customers', { name: 'Khách trả hàng', phone: '0977000999', address: null, initialDebt: 0 });
  await page.goto(UI + '/sale'); await page.waitForTimeout(1200);
  await pick('hop 40x80 1,4'); await addLine('2'); // 570.000
  { const r = page.waitForResponse((x) => x.url().includes('/api/customers?q=')); await page.keyboard.press('F4'); await page.keyboard.type('khach tra hang'); await r; await page.waitForTimeout(200); await page.keyboard.press('Enter'); await page.waitForTimeout(300); }
  const id9 = await draftId();
  await page.click('.cta'); await page.waitForTimeout(500);
  const box9 = await page.evaluate(() => document.querySelector('.el-message-box')?.innerText);
  check('khach tra 0 -> hoi "Ghi no toan bo 570.000"', box9?.includes('Ghi nợ toàn bộ 570.000'), box9?.replace(/\n+/g, ' | '));
  await page.keyboard.press('Enter'); await page.waitForTimeout(800); // Enter = dong y
  const o9 = await api('GET', `/orders/${id9}`);
  check('ban chiu 570.000', Number(o9.debtAfter) === 570000);
  await api('POST', `/customers/${khach9.id}/debt-payments`, { amount: 570000, note: 'Tra het' });
  await page.goto(UI + '/orders'); await page.waitForTimeout(1000);
  await page.click(`text=${o9.code}`); await page.waitForTimeout(500);
  await page.click('.el-drawer >> text=Hủy hóa đơn'); await page.waitForTimeout(500);
  const confirmText = await page.evaluate(() => document.querySelector('.el-message-box__message')?.innerText);
  check('xac nhan huy bao truoc "TRA LAI KHACH 570.000"', confirmText?.includes('TRẢ LẠI KHÁCH 570.000'), confirmText?.replace(/\n+/g, ' | '));
  await page.click('.el-message-box >> button >> text=Hủy hóa đơn'); await page.waitForTimeout(800);
  const alertText = await page.evaluate(() => document.querySelector('.el-message-box')?.innerText);
  check('sau khi huy: hop thong bao bat buoc "Tra lai ... 570.000"', alertText?.includes('570.000') && alertText?.includes('Đã trả tiền cho khách'), alertText?.replace(/\n+/g, ' | '));
  await page.click('.el-message-box >> text=Đã trả tiền cho khách'); await page.waitForTimeout(300);
  const c9 = (await all('/customers?q=khach tra hang'))[0];
  const oc9 = await api('GET', `/orders/${o9.id}`);
  check('no khach ve 0 (khong am)', Number(c9.debt) === 0, `${c9.debt}`);
  check('hoa don luu tien tra lai 570.000', oc9.status === 'CANCELLED' && Number(oc9.refundAmount) === 570000, `${oc9.refundAmount}`);
  const led9 = await all(`/customers/${khach9.id}/debt-ledger`);
  check('so no ghi "da tra lai khach", tong = no', led9[0].description.includes('đã trả lại khách 570,000') && sum(led9.map((l) => Number(l.increase ?? 0) - Number(l.decrease ?? 0))) === 0, led9[0].description);
  // huy xong, ngan chi tiet van mo va cap nhat trang thai "Da huy" + dong tien tra lai
  const drawer9 = await page.evaluate(() => document.querySelector('.el-drawer .refund')?.innerText);
  check('chi tiet hoa don da huy hien "Da tra lai khach khi huy 570.000"', drawer9?.includes('570.000'), drawer9?.replace(/\n/g, ' '));
  await page.screenshot({ path: process.env.SHOT_DIR ? process.env.SHOT_DIR + '/cancel-refund.png' : '/dev/null' }).catch(() => {});

  // ===== 10. Phieu thu no / sao ke (in tu so cong no) =====
  const pdfText = async (p) => { // tai PDF qua API, doc chu bang pdftotext
    const r = await fetch(API + p, { headers: { Authorization: 'Bearer ' + token } });
    const f = path.join(os.tmpdir(), 'e2e-' + Date.now() + '.pdf');
    fs.writeFileSync(f, Buffer.from(await r.arrayBuffer()));
    return { status: r.status, type: r.headers.get('content-type'), text: execFileSync('pdftotext', ['-layout', f, '-']).toString() };
  };
  const reconciles = (doc) => { // moi dong: con no truoc + tang - giam = con no
    let prev = doc.lines[0].ledgerId == null ? Number(doc.lines[0].balance) : 0;
    for (const l of doc.lines.slice(doc.lines[0].ledgerId == null ? 1 : 0)) {
      if (prev + Number(l.increase ?? 0) - Number(l.decrease ?? 0) !== Number(l.balance)) return false;
      prev = Number(l.balance);
    }
    return true;
  };
  const k10 = await api('POST', '/customers', { name: 'Khách sao kê', phone: '0911222333', address: 'Yên Mô', initialDebt: 1000000 });
  await page.goto(UI + '/customers'); await page.waitForTimeout(1000);
  await page.click('text=Khách sao kê'); await page.waitForTimeout(600);
  await page.focus('#amt'); await page.keyboard.type('300.000'); await page.keyboard.press('Enter'); await page.waitForTimeout(700);
  const paidDlg = await page.evaluate(() => document.querySelector('.el-dialog')?.innerText ?? '');
  const pt1 = (paidDlg.match(/PT\d{6}/) || [])[0];
  check('thu no xong: hien so phieu + nut In phieu thu', !!pt1 && paidDlg.includes('300.000') && paidDlg.includes('In phiếu thu'), paidDlg.replace(/\n+/g, ' | '));
  const pdfResp = page.waitForResponse((r) => r.url().includes('/receipt.pdf'));
  await page.click('.el-dialog >> text=In phiếu thu');
  const pr = await pdfResp;
  check('bam In phieu thu -> PDF', pr.status() === 200 && pr.headers()['content-type'].includes('pdf'));
  // mua chiu 32.400? dung san pham Vit: 3 hop x 45.000 = 135.000, tra 100.000 luc mua
  { const o = await api('POST', '/orders');
    await api('POST', `/orders/${o.id}/items`, { productId: P['Vít bắn tôn'].id, quantity: 3, sheets: 0, factor: 0, unitPrice: 45000 });
    await api('POST', `/orders/${o.id}/checkout`, { customerId: k10.id, shippingFee: 0, paidAmount: 100000, expectedPreviousDebt: 700000 }); }
  // thu not bang chuyen khoan qua giao dien
  await page.goto(UI + '/customers'); await page.waitForTimeout(900);
  await page.click('text=Khách sao kê'); await page.waitForTimeout(600);
  await page.click('.pay >> text=Chuyển khoản');
  await page.click('.pay >> text=Trả hết'); await page.fill('.pay input[placeholder^="Ghi chú"]', 'Con trai chuyển hộ');
  await page.click('.pay button[type=submit]'); await page.waitForTimeout(700);
  await page.click('.el-dialog >> text=Đóng'); await page.waitForTimeout(400);
  const led10 = await all(`/customers/${k10.id}/debt-ledger`);
  const last10 = led10[0];
  check('thu het no: no ve 0, phieu ghi chuyen khoan + nguoi thu', Number((await api('GET', `/customers/${k10.id}`)).debt) === 0 && last10.method === 'Chuyển khoản' && !!last10.createdBy, `${last10.description} / ${last10.createdBy}`);
  const doc10 = await api('GET', `/customers/debt-payments/${last10.id}/receipt`);
  check('phieu tra het no van du dong (khong trong)', doc10.lines.length === 5, doc10.lines.map((l) => l.description).join(' | '));
  check('phieu thu: moi dong cong tru khop', reconciles(doc10));
  check('phieu thu: no truoc - thu = con no = 0', Number(doc10.totals[0].amount) - Number(doc10.totals[1].amount) === 0 && Number(doc10.totals[2].amount) === 0 && Number(doc10.totals[0].amount) === 735000, doc10.totals.map((t) => t.label + ' ' + t.amount).join(', '));
  const t10 = await pdfText(`/customers/debt-payments/${last10.id}/receipt.pdf`);
  check('PDF phieu thu: tieu de, so phieu, cac dong, tong ket', t10.status === 200 && t10.text.includes('PHIẾU THU NỢ') && t10.text.includes(last10.receiptNo)
    && t10.text.includes('Mua HĐ') && t10.text.includes('Trả khi mua HĐ') && t10.text.includes("735,000") && t10.text.includes('Con trai chuyển hộ'), t10.text.split('\n').filter((x) => /Thu nợ|Mua|Trả|Còn nợ/.test(x)).join(' | ').replace(/\s+/g, ' ').slice(0, 300));
  // huy phieu dau tien qua giao dien
  await page.click(`.el-drawer button[aria-label="Hủy phiếu ${pt1}"]`); await page.waitForTimeout(400);
  await page.fill('.el-message-box input', 'Thu nhầm, khách chưa đưa tiền');
  await page.click('.el-message-box >> button >> text=Hủy phiếu thu'); await page.waitForTimeout(800);
  check('huy phieu: no tang lai 300.000', Number((await api('GET', `/customers/${k10.id}`)).debt) === 300000);
  const v10 = await pdfText(`/customers/debt-payments/${led10.find((l) => l.receiptNo === pt1).id}/receipt.pdf`);
  check('in lai phieu da huy co dau "DA HUY" + ly do', v10.text.includes('ĐÃ HỦY') && v10.text.includes('Thu nhầm'), '');
  check('phieu da huy: so lieu in lai giu nguyen (300,000 / 700,000)', v10.text.includes('300,000') && v10.text.includes('700,000'));
  check('so no tren man hinh gach dong phieu da huy', await page.evaluate((no) => [...document.querySelectorAll('.el-drawer tr.voided')].some((tr) => tr.innerText.includes(no)), pt1));
  // dieu chinh no: bat buoc ly do
  await page.click('.el-drawer >> text=Điều chỉnh nợ'); await page.waitForTimeout(400);
  await page.focus('#adj-debt'); await page.keyboard.press('Control+A'); await page.keyboard.type('250.000');
  check('dieu chinh: chua co ly do -> nut Luu khoa', await page.evaluate(() => [...document.querySelectorAll('.el-dialog button')].find((b) => b.innerText.includes('Lưu điều chỉnh'))?.disabled));
  await page.click('.el-dialog >> text=Giảm giá, bớt tiền cho khách');
  await page.click('.el-dialog >> text=Lưu điều chỉnh'); await page.waitForTimeout(700);
  const adj = (await all(`/customers/${k10.id}/debt-ledger`))[0];
  check('dieu chinh ghi dong "Dieu chinh: <ly do>", giam 50.000', adj.description === 'Điều chỉnh: Giảm giá, bớt tiền cho khách' && Number(adj.decrease) === 50000, adj.description);
  // sao ke tu dau qua giao dien
  await page.click('.el-drawer >> text=In sao kê'); await page.waitForTimeout(400);
  await page.click('.el-dialog >> text=Từ đầu');
  const stResp = page.waitForResponse((r) => r.url().includes('/statement.pdf'));
  await page.click('.el-dialog >> button >> text=In sao kê');
  check('in sao ke -> PDF', (await stResp).status() === 200);
  const st10 = await api('GET', `/customers/${k10.id}/statement`);
  check('sao ke: cong tru khop, cuoi ky = no hien tai 250.000', reconciles(st10) && Number(st10.totals[3].amount) === 250000
    && Number(st10.totals[0].amount) + Number(st10.totals[1].amount) - Number(st10.totals[2].amount) === 250000, st10.totals.map((t) => t.label + ' ' + t.amount).join(', '));

  // ===== 11. Hai may: may khac thu no trong luc dang ban -> chot bi chan, tai lai no cu =====
  await page.goto(UI + '/sale'); await page.waitForTimeout(1200);
  await pick('vit ban ton'); await addLine('1');
  { const r = page.waitForResponse((x) => x.url().includes('/api/customers?q=')); await page.keyboard.press('F4'); await page.keyboard.type('khach sao ke'); await r; await page.waitForTimeout(200); await page.keyboard.press('Enter'); await page.waitForTimeout(300); }
  const shown = await page.evaluate(() => [...document.querySelectorAll('.sum > div')].find((d) => d.innerText.startsWith('Nợ cũ'))?.querySelector('dd')?.innerText);
  await api('POST', `/customers/${k10.id}/debt-payments`, { amount: 50000, method: 'CASH' }); // may khac
  const id11 = await draftId();
  await page.click('.quick >> text=Ghi nợ hết');
  await page.click('.cta'); await page.waitForTimeout(900);
  check('da bam "Ghi no het" -> khong hoi lai', !(await page.isVisible('.el-message-box')));
  check('chot khi no cu da doi -> bi chan, van la hoa don nhap', (await api('GET', `/orders/${id11}`)).status === 'DRAFT');
  const shown2 = await page.evaluate(() => [...document.querySelectorAll('.sum > div')].find((d) => d.innerText.startsWith('Nợ cũ'))?.querySelector('dd')?.innerText);
  check('phieu tu tai lai no cu moi (250.000 -> 200.000)', shown === '250.000' && shown2 === '200.000', `${shown} -> ${shown2}`);
  await page.click('.cta'); await page.waitForTimeout(900);
  const o11 = await api('GET', `/orders/${id11}`);
  check('chot lai: PAID voi no cu dung 200.000', o11.status === 'PAID' && Number(o11.previousDebt) === 200000);

  // ===== 12. Ghi no het: quen nhap tien khach tra -> hoi, chon "Nhap tien khach tra" -> ve o Khach tra, chua chot =====
  await page.click('.done-actions >> text=Hóa đơn mới'); await page.waitForTimeout(800);
  await pick('vit ban ton'); await addLine('2');
  { const r = page.waitForResponse((x) => x.url().includes('/api/customers?q=')); await page.keyboard.press('F4'); await page.keyboard.type('khach sao ke'); await r; await page.waitForTimeout(200); await page.keyboard.press('Enter'); await page.waitForTimeout(300); }
  const id12 = await draftId();
  await page.keyboard.press('F9'); await page.keyboard.press('Enter'); await page.waitForTimeout(500);
  check('F9 Enter, khach tra 0 -> hoi ghi no het', await page.isVisible('.el-message-box'));
  await page.click('.el-message-box >> text=Nhập tiền khách trả'); await page.waitForTimeout(400);
  check('chon "Nhap tien khach tra" -> chua chot, con tro o Khach tra', (await api('GET', `/orders/${id12}`)).status === 'DRAFT' && (await focused()) === 'paid');
  await page.keyboard.type('100.000'); await page.keyboard.press('F9'); await page.keyboard.press('Enter'); await page.waitForTimeout(800);
  const o12 = await api('GET', `/orders/${id12}`);
  check('co nhap tien tra -> chot ngay, khong hoi', o12.status === 'PAID' && Number(o12.paidAmount) === 100000, `${o12.status} ${o12.paidAmount}`);

  // ===== 13. Nhap hang chi bang ban phim: Enter chon NCC / san pham, SL Enter -> Gia, Gia Enter -> dong moi =====
  const ncc = await api('POST', '/suppliers', { name: 'Đại lý Tôn Hoa Sen Q7', phone: '0283777888', address: null });
  await page.goto(UI + '/receipts/new'); await page.waitForTimeout(1200);
  let keys13 = 0;
  const k = async (key) => { keys13++; await page.keyboard.press(key); };
  const t = async (txt) => { keys13 += [...txt].length; await page.keyboard.type(txt); };
  const waitQ = (pathname, q) => page.waitForResponse((r) => { const u = new URL(r.url()); return u.pathname === pathname && u.searchParams.get('q') === q; });
  await page.focus('#sup');
  { const r = waitQ('/api/suppliers', 'hoa sen'); await t('hoa sen'); await r; await page.waitForTimeout(100); }
  await k('Enter'); await page.waitForTimeout(300);
  const supText = await page.evaluate(() => document.querySelector('#sup')?.closest('.el-select')?.innerText);
  const onProduct = await page.evaluate(() => document.activeElement?.closest('.lines') != null && document.activeElement?.getAttribute('role') === 'combobox');
  check('NCC: go + Enter -> chon, con tro sang o San pham', supText?.includes('Hoa Sen') && onProduct, supText);
  { const r = waitQ('/api/products', 'ton lanh 0,4'); await t('ton lanh 0,4'); await r; await page.waitForTimeout(100); }
  await k('Enter'); await page.waitForTimeout(500);
  check('San pham: go + Enter -> chon ket qua dau, con tro sang SL nhap', await page.evaluate(() => document.activeElement?.closest('[aria-label="Số lượng nhập"]') != null || document.activeElement?.getAttribute('aria-label') === 'Số lượng nhập'));
  await t('100'); await k('Enter'); await t('70.000'); await k('Enter'); await page.waitForTimeout(300);
  // go nhanh roi Enter ngay (ket qua chua ve) van chon dung
  await t('hop 40x80 1,4'); await k('Enter'); await page.waitForTimeout(1000);
  await t('20'); await k('Enter'); await t('250.000'); await k('Enter'); await page.waitForTimeout(300);
  await page.click('.quick >> text=Trả đủ'); await page.click('text=Ghi sổ, nhập kho'); await page.waitForTimeout(300);
  await page.click('.el-message-box__btns >> text=Ghi sổ'); await page.waitForTimeout(1200);
  const rid = Number(new URL(page.url()).pathname.split('/').pop());
  const rc = rid ? await api('GET', `/receipts/${rid}`) : null;
  check('phieu nhap: 2 dong dung san pham / SL / gia, da ghi so', rc?.status === 'POSTED' && rc.lines.length === 2
    && rc.lines[0].productName === 'Tôn lạnh 0,4 ly' && Number(rc.lines[0].inputQty) === 100 && Number(rc.lines[0].inputPrice) === 70000
    && rc.lines[1].productName === 'Hộp sắt 40x80 dày 1,4' && Number(rc.lines[1].inputQty) === 20 && Number(rc.lines[1].inputPrice) === 250000,
    JSON.stringify(rc?.lines?.map((l) => [l.productName, l.inputQty, l.inputPrice])));
  check(`phieu nhap 2 dong: ${keys13} phim, 0 mui ten, 0 lan bam chuot khi nhap dong (truoc: 59 phim + 10 lan bam)`, keys13 === 57, String(keys13));

  await browser.close();
  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} PASS`);
  process.exit(failed.length ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(2); });
