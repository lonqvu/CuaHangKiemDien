/*
 * Kiem thu CHI TIET TUNG MAN HINH tren backend THAT + frontend that (khong mock): thao tac nhu nguoi dung tren giao dien
 * (them / sua / xoa / loc / in...) roi doc lai server de doi chieu; moi man con kiem tra giao dien o 3 do rong
 * (390 / 1366 / 1920): khong tran ngang, o nhap cung hang thang nhau, khong cat chu "...", khong loi JS / 5xx.
 * Bo sung cho e2e-ban-hang.cjs (luong ban hang / cong no) va it-toan-he-thong.cjs (API, phan quyen, bao cao).
 *
 * Chay giong e2e-ban-hang.cjs (backend H2 TRONG tren :8090, frontend dev :5173):
 *   node scripts/test-tung-man.cjs        # PASS/FAIL tung muc, exit 1 neu co FAIL
 * Tao du lieu moi moi lan chay (tien to "ZT") -> chi chay tren DB trong, KHONG chay tren DB that.
 */
const { chromium } = require('playwright');

const API = (process.env.API_URL ?? 'http://localhost:8090') + '/api';
const UI = process.env.UI_URL ?? 'http://localhost:5173';
const TZ = process.env.SHOP_TZ ?? 'Asia/Ho_Chi_Minh';
const TODAY = new Intl.DateTimeFormat('en-CA', { timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const results = [];
let section = '';
function check(name, ok, detail = '') {
  results.push({ section, name, ok: !!ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  [${section}] ${name}${detail && (!ok || process.env.VERBOSE) ? '  — ' + String(detail).slice(0, 300) : ''}`);
}
let token;
async function api(method, p, body) {
  const r = await fetch(API + p, { method, headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  if (r.status >= 300) throw new Error(`${method} ${p} -> ${r.status} ${t.slice(0, 200)}`);
  return t ? JSON.parse(t) : null;
}
const all = async (p) => { const r = await api('GET', p + (p.includes('?') ? '&' : '?') + 'size=500'); return r.items ?? r; };
/** San pham theo id (khong co GET /products/{id}): tim trong danh sach dang ban, roi ngung ban. */
async function prod(id) {
  for (const active of [true, false]) { const p = (await all(`/products?active=${active}`)).find((x) => x.id === id); if (p) return { ...p, active }; }
  return null;
}
const N = (v) => Number(String(v ?? '').replace(/[^\d,-]/g, '').replace(',', '.'));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  token = (await (await fetch(API + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: 'admin123' }) })).json()).token;
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ timezoneId: TZ, viewport: { width: 1366, height: 860 } });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(`JS: ${e.message}`));
  page.on('response', (r) => { if (r.url().includes('/api/') && r.status() >= 500) errors.push(`${r.status()} ${r.request().method()} ${r.url().split('/api')[1]}`); });
  page.on('dialog', (d) => d.accept()); // window.open / confirm cua trinh duyet (neu co)

  // ---------- helpers ----------
  const dlg = () => page.locator('.el-dialog:visible').last();
  const field = (label) => dlg().locator(`.el-form-item:has(.el-form-item__label:text-is("${label}")) input`).first();
  const fill = async (loc, v) => { await loc.click(); await page.keyboard.press('Control+A'); await page.keyboard.type(String(v)); };
  const lastMsg = () => page.evaluate(() => [...document.querySelectorAll('.el-message')].map((e) => e.innerText).pop() ?? '');
  const go = async (path) => { await page.goto(UI + path); await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(400); };
  const boxConfirm = async () => { await page.locator('.el-message-box__btns .el-button--primary').click(); await page.waitForTimeout(500); };
  const pdfOk = (urlPart) => page.waitForResponse((r) => r.url().includes(urlPart), { timeout: 10000 }).then((r) => r.status() === 200 && (r.headers()['content-type'] ?? '').includes('pdf')).catch(() => false);
  const errMark = () => errors.length;
  const noErrors = (from, label) => check(`${label}: khong loi JS / 5xx`, errors.length === from, errors.slice(from).join(' | '));

  /** Giao dien mot man o 3 do rong: tran ngang, o cung hang thang nhau (trong cung panel / hop thoai), chu bi cat "...". */
  async function layout(path, name, prepare) {
    const out = [];
    for (const w of [390, 1366, 1920]) {
      const c = await browser.newContext({ timezoneId: TZ, viewport: { width: w, height: w === 390 ? 844 : 900 }, ...(w === 390 ? { isMobile: true, hasTouch: true } : {}) });
      const p = await c.newPage();
      const errs = [];
      p.on('pageerror', (e) => errs.push(e.message));
      await p.goto(UI + '/login'); await p.fill('input[autocomplete=username]', 'admin'); await p.fill('input[type=password]', 'admin123');
      await p.click('button[type=submit]'); await p.waitForURL(/dashboard/);
      await p.goto(UI + path); await p.waitForLoadState('networkidle').catch(() => {}); await p.waitForTimeout(600);
      if (prepare) await prepare(p);
      const m = await p.evaluate(() => {
        const vis = (e) => e.offsetParent !== null && e.getBoundingClientRect().width > 0;
        const boxes = [...document.querySelectorAll('.el-input__wrapper, .el-select__wrapper, .el-textarea__inner')].filter(vis)
          .map((e) => ({ r: e.getBoundingClientRect(), box: e.closest('.el-dialog, .el-drawer, .panel, .toolbar, form, header') }));
        let mis = 0;
        for (let i = 0; i < boxes.length; i++) for (let j = i + 1; j < boxes.length; j++) {
          if (boxes[i].box !== boxes[j].box) continue;
          const d = Math.abs(boxes[i].r.top - boxes[j].r.top);
          if (d >= 2 && d < 14 && Math.abs(boxes[i].r.height - boxes[j].r.height) < 3) mis++;
        }
        const cut = [...document.querySelectorAll('.el-table .cell')].filter((e) => vis(e) && e.scrollWidth > e.clientWidth + 1 && getComputedStyle(e).textOverflow === 'ellipsis' && !e.closest('.el-table__fixed')).length;
        return { overflowX: document.documentElement.scrollWidth - innerWidth, mis, cut };
      });
      out.push(`${w}:${JSON.stringify(m)}`);
      check(`${name} @${w}px: khong tran ngang`, m.overflowX <= 0, `${m.overflowX}px`);
      check(`${name} @${w}px: o cung hang thang nhau`, m.mis === 0, `${m.mis} cap lech`);
      if (w >= 1366) check(`${name} @${w}px: khong cat chu "..." trong bang`, m.cut === 0, `${m.cut} o`);
      check(`${name} @${w}px: khong loi JS`, errs.length === 0, errs.join(' | '));
      await c.close();
    }
    return out;
  }

  await page.goto(UI + '/login');

  // ===================== 1. DANG NHAP =====================
  section = 'Dang nhap';
  {
    const e0 = errMark();
    await page.fill('input[autocomplete=username]', 'admin'); await page.fill('input[type=password]', 'sai-mat-khau');
    await page.keyboard.press('Enter'); await page.waitForTimeout(800);
    const err = await page.evaluate(() => document.body.innerText);
    check('sai mat khau -> o lai trang dang nhap, bao loi', page.url().includes('/login') && /không đúng|sai|Sai/i.test(err), err.slice(0, 120));
    await page.fill('input[type=password]', 'admin123'); await page.keyboard.press('Enter');
    await page.waitForURL(/dashboard/, { timeout: 8000 }).catch(() => {});
    check('dung mat khau -> vao Tong quan', page.url().includes('/dashboard'));
    noErrors(e0, 'Dang nhap');
    await layout('/login', 'Dang nhap').catch(() => {});
  }

  // ===================== 2. LOAI HANG =====================
  section = 'Loai hang';
  let catTon, catPk;
  {
    const e0 = errMark();
    await go('/categories');
    const addCat = async (name) => { await fill(page.locator('input[aria-label="Tên loại hàng mới"]'), name); await page.keyboard.press('Enter'); await page.waitForTimeout(500); };
    await addCat('ZT Tôn');
    await addCat('ZT Phụ kiện');
    await addCat('ZT Tạm');
    const cats = await api('GET', '/categories');
    catTon = cats.find((c) => c.name === 'ZT Tôn'); catPk = cats.find((c) => c.name === 'ZT Phụ kiện');
    check('them 3 loai -> co tren server va tren bang', !!catTon && !!catPk && (await page.locator('.el-table__body').innerText()).includes('ZT Tạm'));
    await addCat('zt tôn');
    check('them trung ten (khac hoa thuong) -> bao loi, khong tao them', (await api('GET', '/categories')).filter((c) => c.name.toLowerCase() === 'zt tôn').length === 1, await lastMsg());
    const row = page.locator('.el-table__row', { hasText: 'ZT Tạm' });
    await row.locator('text=Đổi tên').click(); // dong dang sua: ten thanh o nhap (chi 1 o luc nay)
    await fill(page.locator('form.inline input[aria-label="Tên loại"]'), 'ZT Tạm 2'); await page.locator('form.inline button:has-text("Lưu")').click(); await page.waitForTimeout(500);
    check('doi ten -> server doi', (await api('GET', '/categories')).some((c) => c.name === 'ZT Tạm 2'));
    await page.locator('.el-table__row', { hasText: 'ZT Tạm 2' }).locator('button:has-text("Xóa")').click(); await boxConfirm();
    check('xoa loai khong co san pham -> mat', !(await api('GET', '/categories')).some((c) => c.name.startsWith('ZT Tạm')));
    noErrors(e0, 'Loai hang');
  }

  // ===================== 3. SAN PHAM =====================
  section = 'San pham';
  let pTon, pVit;
  {
    const e0 = errMark();
    await go('/products');
    const addProduct = async ({ name, cat, unit, price, min, conv }) => {
      await page.locator('button:has-text("Thêm sản phẩm")').first().click(); await page.waitForTimeout(300);
      await fill(field('Tên sản phẩm'), name);
      if (cat) { await dlg().locator('.el-form-item:has(.el-form-item__label:text-is("Loại hàng")) .el-select').click(); await page.locator('.el-select-dropdown__item:visible', { hasText: cat }).click(); }
      await fill(field('Đơn vị tính'), unit);
      await fill(dlg().locator('.el-form-item:has(.el-form-item__label:text-is("Giá bán (₫)")) input'), price);
      const shown = await dlg().locator('.el-form-item:has(.el-form-item__label:text-is("Giá bán (₫)")) input').inputValue();
      if (min) await fill(dlg().locator('.el-form-item:has(.el-form-item__label:text-matches("Tồn tối thiểu")) input'), min);
      if (conv) {
        await dlg().locator('button:has-text("Thêm đơn vị quy đổi")').click();
        await fill(dlg().locator('input[placeholder="kg, bó, cuộn"]').last(), conv[0]);
        await fill(dlg().locator('input[aria-label="Tỷ lệ quy đổi"]').last(), conv[1]);
      }
      await dlg().locator('.el-dialog__footer button.el-button--primary').click(); await page.waitForTimeout(700);
      return shown;
    };
    const shown = await addProduct({ name: 'ZT Tôn lạnh 0,45 ly', cat: 'ZT Tôn', unit: 'm', price: '105000', min: '20', conv: ['cuộn', '300'] });
    check('o gia ban tu nhom nghin khi go: 105000 -> 105.000', shown === '105.000', shown);
    await addProduct({ name: 'ZT Vít bắn tôn 5cm', cat: 'ZT Phụ kiện', unit: 'hộp', price: '85000' });
    const prods = await all('/products?q=ZT');
    pTon = prods.find((p) => p.name === 'ZT Tôn lạnh 0,45 ly'); pVit = prods.find((p) => p.name === 'ZT Vít bắn tôn 5cm');
    check('them san pham -> dung loai / don vi / gia', pTon?.categoryName === 'ZT Tôn' && pTon?.unit === 'm' && Number(pTon?.price) === 105000 && Number(pVit?.price) === 85000, JSON.stringify(pTon));
    const units = await api('GET', `/products/${pTon.id}/units`);
    check('don vi quy doi: 1 cuon = 300 m', units.some((u) => u.unit === 'cuộn' && Number(u.ratio) === 300), JSON.stringify(units));
    // tim khong dau
    await fill(page.locator('input[placeholder="Tìm tên sản phẩm"]'), 'zt ton lanh'); await page.waitForTimeout(700);
    const rows = await page.locator('.el-table__body .el-table__row').allInnerTexts();
    check('tim khong dau "zt ton lanh" -> ra dung 1 san pham', rows.length === 1 && rows[0].includes('ZT Tôn lạnh 0,45 ly'), rows.join(' | '));
    // sua gia
    await page.locator('.el-table__row', { hasText: 'ZT Tôn lạnh' }).locator('button:has-text("Sửa")').click(); await page.waitForTimeout(300);
    await fill(dlg().locator('.el-form-item:has(.el-form-item__label:text-is("Giá bán (₫)")) input'), '110000');
    await dlg().locator('.el-dialog__footer button.el-button--primary').click(); await page.waitForTimeout(700);
    check('sua gia 110.000 -> server', Number((await prod(pTon.id)).price) === 110000);
    // ngung ban / ban lai
    await page.locator('.el-table__row', { hasText: 'ZT Tôn lạnh' }).locator('button:has-text("Ngừng bán")').click(); await boxConfirm();
    const off = (await api('GET', '/products?q=zt ton lanh&active=true')).items.length;
    check('ngung ban -> khong con trong tim kiem hang dang ban', off === 0, `${off}`);
    await page.locator('.toolbar .el-radio-button', { hasText: /Ngừng/ }).click().catch(() => {}); await page.waitForTimeout(600);
    await page.locator('.el-table__row', { hasText: 'ZT Tôn lạnh' }).locator('button:has-text("Bán lại")').click(); await page.waitForTimeout(600);
    check('ban lai -> dang ban', (await prod(pTon.id)).active === true);
    noErrors(e0, 'San pham');
    await layout('/products', 'San pham');
    await layout('/products', 'San pham (hop thoai Them)', async (p) => { await p.locator('button:has-text("Thêm sản phẩm")').first().click(); await p.waitForTimeout(400); });
  }

  // ===================== 4. NHA CUNG CAP =====================
  section = 'Nha cung cap';
  let ncc;
  {
    const e0 = errMark();
    await go('/suppliers');
    await page.locator('button:has-text("Thêm nhà cung cấp")').first().click(); await page.waitForTimeout(300);
    await fill(field('Tên nhà cung cấp'), 'ZT Đại lý tôn Hoa Sen');
    await fill(field('Số điện thoại'), '0283777999');
    await fill(dlg().locator('.el-form-item:has(.el-form-item__label:text-is("Nợ ban đầu (₫)")) input'), '2000000');
    await dlg().locator('.el-dialog__footer button.el-button--primary').click(); await page.waitForTimeout(700);
    ncc = (await all('/suppliers?q=hoa sen'))[0];
    check('them NCC co no ban dau 2.000.000', ncc && Number(ncc.debt) === 2000000, JSON.stringify(ncc));
    if (await page.locator('.el-drawer:visible').count()) { await page.keyboard.press('Escape'); await page.waitForTimeout(400); } // them xong app tu mo chi tiet
    await fill(page.locator('input[placeholder="Tên, SĐT hoặc địa chỉ"]'), 'dai ly ton hoa sen'); await page.waitForTimeout(700);
    check('tim NCC khong dau', (await page.locator('.el-table__body').innerText()).includes('ZT Đại lý tôn Hoa Sen'));
    await page.locator('.el-table__row', { hasText: 'ZT Đại lý tôn Hoa Sen' }).click(); await page.waitForTimeout(700);
    await fill(page.locator('.el-drawer #amt'), '500000');
    await page.locator('.el-drawer button[type=submit]').click(); await page.waitForTimeout(800);
    check('tra NCC 500.000 -> con no 1.500.000', Number((await api('GET', `/suppliers/${ncc.id}`)).debt) === 1500000);
    await page.locator('.el-drawer button:has-text("Sửa số nợ")').click(); await page.waitForTimeout(300);
    await fill(page.locator('.el-message-box input'), '1200000'); await boxConfirm();
    check('sua so no NCC -> 1.200.000', Number((await api('GET', `/suppliers/${ncc.id}`)).debt) === 1200000);
    await page.keyboard.press('Escape'); await page.waitForTimeout(300);
    noErrors(e0, 'Nha cung cap');
    await layout('/suppliers', 'Nha cung cap');
    await layout(`/suppliers/${ncc.id}`, 'Nha cung cap (chi tiet)');
  }

  // ===================== 5. PHIEU NHAP =====================
  section = 'Phieu nhap';
  {
    const e0 = errMark();
    const before = Number((await api('GET', `/stock/${pTon.id}`)).stock.onHand);
    await go('/receipts/new');
    const waitQ = (pathname, q) => page.waitForResponse((r) => { const u = new URL(r.url()); return u.pathname === pathname && u.searchParams.get('q') === q; }, { timeout: 8000 }).catch(() => null);
    await page.focus('#sup'); { const r = waitQ('/api/suppliers', 'hoa sen'); await page.keyboard.type('hoa sen'); await r; await page.waitForTimeout(150); } await page.keyboard.press('Enter'); await page.waitForTimeout(300);
    { const r = waitQ('/api/products', 'zt ton lanh'); await page.keyboard.type('zt ton lanh'); await r; await page.waitForTimeout(150); } await page.keyboard.press('Enter'); await page.waitForTimeout(500);
    await page.keyboard.type('100'); await page.keyboard.press('Enter'); await page.keyboard.type('70000'); await page.keyboard.press('Enter'); await page.waitForTimeout(300);
    { const r = waitQ('/api/products', 'zt vit'); await page.keyboard.type('zt vit'); await r; await page.waitForTimeout(150); } await page.keyboard.press('Enter'); await page.waitForTimeout(500);
    await page.keyboard.type('20'); await page.keyboard.press('Enter'); await page.keyboard.type('60000'); await page.keyboard.press('Enter'); await page.waitForTimeout(300);
    const priceShown = await page.locator('input[aria-label="Giá nhập"]').first().inputValue();
    const sfx = await page.locator('.nf-suffix').allInnerTexts();
    check('gia nhap tu nhom nghin + don vi trong o', priceShown === '70.000' && sfx.includes('/m') && sfx.includes('/hộp'), `${priceShown} ${sfx}`);
    const total = await page.locator('.sum .total dd').innerText();
    check('tong tien hang = 100x70.000 + 20x60.000 = 8.200.000', N(total) === 8200000, total);
    await page.locator('button:has-text("Lưu nháp")').click(); await page.waitForTimeout(900);
    const rid = Number(new URL(page.url()).pathname.split('/').pop());
    check('luu nhap -> co ma phieu, trang thai Nhap, chua vao kho', rid > 0 && (await api('GET', `/receipts/${rid}`)).status === 'DRAFT' && Number((await api('GET', `/stock/${pTon.id}`)).stock.onHand) === before);
    await page.locator('button:has-text("Ghi sổ, nhập kho")').click(); await page.waitForTimeout(300); await boxConfirm(); await page.waitForTimeout(600);
    const rc = await api('GET', `/receipts/${rid}`);
    check('ghi so -> POSTED, ton tang 100, no NCC tang 8.200.000', rc.status === 'POSTED' && Number((await api('GET', `/stock/${pTon.id}`)).stock.onHand) === before + 100
      && Number((await api('GET', `/suppliers/${ncc.id}`)).debt) === 1200000 + 8200000, `${rc.status}`);
    // phieu thu 2 -> huy -> hoan kho + no
    const r2 = await api('POST', '/receipts', { supplierId: ncc.id, note: 'ZT huy', paidAmount: 0, lines: [{ productId: pVit.id, inputUnit: 'hộp', inputQty: 5, inputPrice: 60000, qty: null }] });
    await api('POST', `/receipts/${r2.id}/post`);
    const vitMid = Number((await api('GET', `/stock/${pVit.id}`)).stock.onHand);
    await go(`/receipts/${r2.id}`);
    await page.locator('button:has-text("Hủy phiếu")').click(); await boxConfirm(); await page.waitForTimeout(600);
    check('huy phieu da ghi so -> tru lai kho 5, hoan no NCC', (await api('GET', `/receipts/${r2.id}`)).status === 'CANCELLED' && Number((await api('GET', `/stock/${pVit.id}`)).stock.onHand) === vitMid - 5
      && Number((await api('GET', `/suppliers/${ncc.id}`)).debt) === 9400000);
    await go('/receipts');
    const list = await page.locator('.el-table__body').innerText();
    check('danh sach phieu nhap hien phieu vua ghi so', list.includes(rc.code), rc.code);
    noErrors(e0, 'Phieu nhap');
    await layout('/receipts', 'Danh sach phieu nhap');
    await layout(`/receipts/${rid}`, 'Phieu nhap (da ghi so)');
    await layout('/receipts/new', 'Phieu nhap moi');
  }

  // ===================== 6. TON KHO =====================
  section = 'Ton kho';
  {
    const e0 = errMark();
    await go('/inventory');
    await fill(page.locator('input[placeholder="Tìm tên sản phẩm"]'), 'zt ton'); await page.waitForTimeout(700);
    const row = await page.locator('.el-table__row', { hasText: 'ZT Tôn lạnh' }).innerText();
    check('bang ton kho: ZT Ton 100 m', row.includes('100'), row.replace(/\n/g, ' '));
    await page.locator('.el-table__row', { hasText: 'ZT Tôn lạnh' }).click(); await page.waitForTimeout(800);
    await fill(page.locator('.el-drawer #min'), '150'); await page.locator('.el-drawer button:has-text("Lưu")').click(); await page.waitForTimeout(600);
    check('dat ton toi thieu 150', Number((await prod(pTon.id)).minStock) === 150);
    await page.locator('.el-drawer button:has-text("Điều chỉnh / xuất hủy")').click(); await page.waitForTimeout(300);
    await fill(dlg().locator('.num-input input'), '5');
    await dlg().locator('.el-select input').click(); await page.keyboard.type('Hư hỏng khi vận chuyển'); await page.keyboard.press('Enter');
    await dlg().locator('.el-dialog__footer button:has-text("Ghi xuất kho")').click(); await page.waitForTimeout(800);
    const st = await api('GET', `/stock/${pTon.id}`);
    const mv = (await all(`/stock/${pTon.id}/movements`))[0];
    check('xuat huy 5 -> ton 95, the kho co dong ly do', Number(st.stock.onHand) === 95 && Number(mv.qty) === -5 && JSON.stringify(mv).includes('Hư hỏng'), JSON.stringify(mv));
    await page.keyboard.press('Escape'); await page.waitForTimeout(300);
    await page.locator('.el-radio-button', { hasText: 'Sắp hết' }).click(); await page.waitForTimeout(700);
    check('loc "Sap het" -> co ZT Ton (95 < 150)', (await page.locator('.el-table__body').innerText()).includes('ZT Tôn lạnh'));
    noErrors(e0, 'Ton kho');
    await layout('/inventory', 'Ton kho');
    await layout(`/inventory/${pTon.id}`, 'The kho');
  }

  // ===================== 7. KIEM KE =====================
  section = 'Kiem ke';
  {
    const e0 = errMark();
    await go('/stock-counts');
    await page.locator('button:has-text("Lập phiếu kiểm kê")').first().click(); await page.waitForTimeout(300);
    await fill(field('Ghi chú'), 'ZT kiem ke cuoi thang');
    await dlg().locator('.el-select').click(); await page.locator('.el-select-dropdown__item:visible', { hasText: 'ZT Tôn' }).click();
    await dlg().locator('.el-dialog__footer button.el-button--primary').click(); await page.waitForTimeout(1000);
    const scId = Number(new URL(page.url()).pathname.split('/').pop());
    check('lap phieu kiem ke theo loai -> mo phieu', scId > 0, page.url());
    await fill(page.locator(`input[aria-label="Số đếm ZT Tôn lạnh 0,45 ly"]`), '93'); await page.keyboard.press('Tab'); await page.waitForTimeout(300);
    const diff = await page.locator('.el-table__row', { hasText: 'ZT Tôn lạnh' }).innerText();
    check('chenh lech hien "Thieu 2"', diff.includes('Thiếu 2'), diff.replace(/\n/g, ' '));
    await page.locator('button:has-text("Ghi sổ")').click(); await boxConfirm(); await page.waitForTimeout(800);
    check('ghi so kiem ke -> ton = 93', Number((await api('GET', `/stock/${pTon.id}`)).stock.onHand) === 93);
    noErrors(e0, 'Kiem ke');
    await layout('/stock-counts', 'Danh sach kiem ke');
    await layout(`/stock-counts/${scId}`, 'Phieu kiem ke');
  }

  // ===================== 8. KHACH HANG & CONG NO =====================
  section = 'Khach hang';
  let kh;
  {
    const e0 = errMark();
    await go('/customers');
    await page.locator('button:has-text("Thêm khách hàng")').first().click(); await page.waitForTimeout(300);
    await fill(field('Tên khách'), 'ZT Anh Ba thợ sắt');
    await fill(field('Số điện thoại'), '0911222333');
    await fill(field('Địa chỉ'), 'Thôn Đông');
    await fill(dlg().locator('.el-form-item:has(.el-form-item__label:text-is("Nợ ban đầu (₫)")) input'), '1000000');
    await dlg().locator('.el-dialog__footer button.el-button--primary').click(); await page.waitForTimeout(700);
    kh = (await all('/customers?q=anh ba tho sat'))[0];
    check('them khach co no ban dau 1.000.000', kh && Number(kh.debt) === 1000000, JSON.stringify(kh));
    if (await page.locator('.el-drawer:visible').count()) { await page.keyboard.press('Escape'); await page.waitForTimeout(400); }
    await fill(page.locator('input[placeholder="Tên, số điện thoại hoặc địa chỉ"]'), '2333'); await page.waitForTimeout(700);
    check('tim theo 4 so cuoi SDT', (await page.locator('.el-table__body').innerText()).includes('ZT Anh Ba'));
    await page.locator('.el-table__row', { hasText: 'ZT Anh Ba' }).click(); await page.waitForTimeout(800);
    check('nut "An khach" bi khoa khi con no', await page.locator('.el-drawer button:has-text("Ẩn khách khỏi danh sách")').isDisabled());
    // thu no + in phieu thu
    await fill(page.locator('.el-drawer #amt'), '300000');
    await page.locator('.el-drawer button[type=submit]').click(); await page.waitForTimeout(800);
    check('thu no 300.000 -> con 700.000, hien hop "Da thu no"', Number((await api('GET', `/customers/${kh.id}`)).debt) === 700000 && await page.locator('.el-dialog:visible', { hasText: 'Đã thu nợ' }).count() === 1);
    { const pr = pdfOk('/receipt.pdf'); await page.locator('.el-dialog:visible button:has-text("In phiếu thu")').click(); check('in phieu thu -> PDF 200', await pr); }
    await page.waitForTimeout(400);
    // dieu chinh no
    await page.locator('.el-drawer button:has-text("Điều chỉnh nợ")').click(); await page.waitForTimeout(300);
    await fill(page.locator('#adj-debt'), '650000');
    await dlg().locator('.el-button--small').first().click(); // chon ly do co san
    await dlg().locator('.el-dialog__footer button:has-text("Lưu điều chỉnh")').click(); await page.waitForTimeout(800);
    const led = await all(`/customers/${kh.id}/debt-ledger`);
    check('dieu chinh no -> 650.000, so no co dong dieu chinh (MANUAL, co ly do)', Number((await api('GET', `/customers/${kh.id}`)).debt) === 650000 && led[0].type === 'MANUAL' && !!led[0].description, led[0]?.type);
    // sao ke
    await page.locator('.el-drawer button:has-text("In sao kê")').click(); await page.waitForTimeout(300);
    await dlg().locator('button:has-text("Từ đầu")').click();
    { const pr = pdfOk('/statement.pdf'); await dlg().locator('.el-dialog__footer button:has-text("In sao kê")').click(); check('in sao ke -> PDF 200', await pr); }
    await page.waitForTimeout(400);
    // huy phieu thu -> no tang lai
    const pay = led.find((l) => l.receiptNo && l.canVoid);
    await go(`/customers/${kh.id}`); await page.waitForTimeout(500);
    await page.locator(`.el-drawer button[aria-label="Hủy phiếu ${pay.receiptNo}"]`).click(); await page.waitForTimeout(300);
    const box = page.locator('.el-message-box:visible');
    if (await box.locator('input, textarea').count()) await box.locator('input, textarea').first().fill('Thu nhầm');
    await boxConfirm();
    check('huy phieu thu -> no tang lai 300.000 (950.000)', Number((await api('GET', `/customers/${kh.id}`)).debt) === 950000, (await api('GET', `/customers/${kh.id}`)).debt);
    // sua thong tin
    await page.locator('.el-drawer button:has-text("Sửa thông tin")').click(); await page.waitForTimeout(300);
    await fill(field('Địa chỉ'), 'Thôn Tây'); await dlg().locator('.el-dialog__footer button.el-button--primary').click(); await page.waitForTimeout(600);
    check('sua dia chi -> server', (await api('GET', `/customers/${kh.id}`)).address === 'Thôn Tây');
    await page.keyboard.press('Escape'); await page.waitForTimeout(300);
    noErrors(e0, 'Khach hang');
    await layout('/customers', 'Khach hang');
    await layout(`/customers/${kh.id}`, 'Khach hang (chi tiet)');
  }

  // ===================== 9. BAN HANG (nhanh, chi tiet o e2e-ban-hang) =====================
  section = 'Ban hang';
  let sold;
  {
    const e0 = errMark();
    await go('/sale');
    const waitQ = (q) => page.waitForResponse((r) => { const u = new URL(r.url()); return u.pathname === '/api/products' && u.searchParams.get('q') === q; }, { timeout: 8000 }).catch(() => null);
    await page.keyboard.press('F2'); { const r = waitQ('zt ton lanh'); await page.keyboard.type('zt ton lanh'); await r; await page.waitForTimeout(150); } await page.keyboard.press('Enter'); await page.waitForTimeout(400);
    const qs = [await page.inputValue('#q'), await page.inputValue('#s')];
    await page.keyboard.type('4x2 3x1');
    const locked = [await page.isDisabled('#q'), await page.inputValue('#q'), await page.inputValue('#s')];
    check('chon ton: So luong / So tam = 0; dang go nhieu doan -> khoa, hien trong', qs.join() === '0,0' && locked[0] && locked[1] === '' && locked[2] === '', JSON.stringify([qs, locked]));
    await page.keyboard.press('Enter'); await page.waitForTimeout(600);
    await page.keyboard.press('F2'); { const r = waitQ('zt vit'); await page.keyboard.type('zt vit'); await r; await page.waitForTimeout(150); } await page.keyboard.press('Enter'); await page.waitForTimeout(400);
    check('chon vit (khong ban theo m): So luong mac dinh 1', (await page.inputValue('#q')) === '1');
    await page.keyboard.type('3'); await page.keyboard.press('Enter'); await page.waitForTimeout(600);
    { const r = page.waitForResponse((x) => x.url().includes('/api/customers?q=')); await page.keyboard.press('F4'); await page.keyboard.type('anh ba tho'); await r; await page.waitForTimeout(200); await page.keyboard.press('Enter'); await page.waitForTimeout(400); }
    const ticket = await page.locator('.ticket').innerText();
    // (4x2 + 3x1) x 1.08 = 11.88 m x 110.000 = 1.306.800; + 3 x 85.000 = 255.000 -> 1.561.800
    check('phieu: tien hang 1.561.800, no cu 950.000, tong 2.511.800', ticket.includes('1.561.800') && ticket.includes('950.000') && ticket.includes('2.511.800'), ticket.replace(/\n/g, ' | '));
    check('phieu khach quen: co dong No cu, khong co dong "= so tien" thua', ticket.includes('Nợ cũ') && !/= \d/.test(ticket));
    await fill(page.locator('#paid'), '1000000');
    check('o Khach tra tu nhom nghin', (await page.inputValue('#paid')) === '1.000.000');
    const id = await page.evaluate(() => sessionStorage.getItem('sale.draftId'));
    await page.keyboard.press('F9'); await page.keyboard.press('Enter'); await page.waitForTimeout(900);
    sold = await api('GET', `/orders/${id}`);
    check('chot -> PAID, con no 1.511.800, ton tru 11,88', sold.status === 'PAID' && Number(sold.debtAfter) === 1511800 && Math.abs(Number((await api('GET', `/stock/${pTon.id}`)).stock.onHand) - (93 - 11.88)) < 0.0005, `${sold.status} ${sold.debtAfter}`);
    { const pr = pdfOk(`/orders/${id}/invoice.pdf`); await page.locator('.done-actions button:has-text("In hóa đơn")').click(); check('in hoa don -> PDF 200', await pr); }
    noErrors(e0, 'Ban hang');
    await layout('/sale', 'Ban hang');
  }

  // ===================== 10. HOA DON =====================
  section = 'Hoa don';
  {
    const e0 = errMark();
    await go('/orders');
    await fill(page.locator('input[placeholder="Mã HĐ, tên/SĐT khách, nhân viên, ghi chú"]'), sold.code); await page.waitForTimeout(800);
    const rows = await page.locator('.el-table__body .el-table__row').count();
    check('tim theo ma HD -> 1 dong', rows === 1, `${rows}`);
    await page.locator('.el-radio-button', { hasText: 'Hôm nay' }).click(); await page.waitForTimeout(700);
    check('loc "Hom nay" van thay HD vua ban', (await page.locator('.el-table__body').innerText()).includes(sold.code));
    await page.locator('.el-table__row', { hasText: sold.code }).click(); await page.waitForTimeout(800);
    const drawer = await page.locator('.el-drawer').innerText();
    check('chi tiet HD: dung khach, tong, dong hang', drawer.includes('ZT Anh Ba') && drawer.includes('2.511.800') && drawer.includes('ZT Vít'), drawer.slice(0, 200).replace(/\n/g, ' | '));
    { const pr = pdfOk('/invoice.pdf'); await page.locator('.el-drawer button:has-text("In hóa đơn")').click(); check('in tu chi tiet -> PDF 200', await pr); }
    await page.waitForTimeout(300);
    const stockBefore = Number((await api('GET', `/stock/${pTon.id}`)).stock.onHand);
    await page.locator('.el-drawer button:has-text("Hủy hóa đơn")').click(); await page.waitForTimeout(400);
    await page.locator('.el-message-box__btns button', { hasText: 'Hủy hóa đơn' }).click(); await page.waitForTimeout(900);
    if (await page.locator('.el-message-box:visible').count()) await boxConfirm(); // thong bao tra lai tien (neu co)
    const c = await api('GET', `/orders/${sold.id}`);
    check('huy HD -> CANCELLED, hoan kho, no khach ve 950.000', c.status === 'CANCELLED' && Math.abs(Number((await api('GET', `/stock/${pTon.id}`)).stock.onHand) - (stockBefore + 11.88)) < 0.0005
      && Number((await api('GET', `/customers/${kh.id}`)).debt) === 950000, `${c.status} ${(await api('GET', `/customers/${kh.id}`)).debt}`);
    await page.keyboard.press('Escape'); await page.waitForTimeout(300);
    // tab Da huy + xoa nhap bo do
    await fill(page.locator('input[placeholder="Mã HĐ, tên/SĐT khách, nhân viên, ghi chú"]'), ''); await page.waitForTimeout(300);
    await page.locator('.tabs button, .tabs [role=tab]', { hasText: 'Đã hủy' }).first().click(); await page.waitForTimeout(700);
    check('tab "Da huy" co HD vua huy', (await page.locator('.el-table__body').innerText()).includes(sold.code));
    const draft = await api('POST', '/orders'); await api('POST', `/orders/${draft.id}/items`, { productId: pVit.id, quantity: 1, sheets: 0, factor: 0, unitPrice: 85000 });
    await go('/orders'); await page.locator('.tabs button, .tabs [role=tab]', { hasText: 'Nháp' }).first().click(); await page.waitForTimeout(700);
    await page.locator('.el-table__row').first().locator('button:has-text("Xóa nháp")').click(); await boxConfirm();
    check('xoa hoa don nhap bo do', await fetch(API + `/orders/${draft.id}`, { headers: { Authorization: 'Bearer ' + token } }).then((r) => r.status === 404));
    noErrors(e0, 'Hoa don');
    await layout('/orders', 'Hoa don');
    await layout(`/orders/${sold.id}`, 'Hoa don (chi tiet)');
  }

  // ===================== 11. THONG KE + TONG QUAN =====================
  section = 'Thong ke';
  {
    const e0 = errMark();
    // ban 1 HD da chot de co doanh thu hom nay
    const o = await api('POST', '/orders');
    await api('POST', `/orders/${o.id}/items`, { productId: pVit.id, quantity: 2, sheets: 0, factor: 0, unitPrice: 85000 });
    await api('POST', `/orders/${o.id}/checkout`, { customerId: null, customerName: 'ZT Khách lẻ', paidAmount: 170000, shippingFee: 0 });
    const rev = await api('GET', `/reports/revenue?from=${TODAY}&to=${TODAY}`);
    const revenue = rev.reduce((a, r) => a + Number(r.revenue), 0);
    await go('/reports');
    await page.locator('.el-radio-button', { hasText: 'Hôm nay' }).first().click(); await page.waitForTimeout(1000);
    const cards = await page.locator('.content').innerText();
    check(`the Doanh thu hom nay = API (${revenue.toLocaleString('vi-VN')})`, cards.includes(revenue.toLocaleString('vi-VN')), cards.slice(0, 300).replace(/\n/g, ' | '));
    check('top san pham co ZT Vit', cards.includes('ZT Vít'));
    for (const k of ['7 ngày', 'Tháng này', 'Năm nay']) { await page.locator('.el-radio-button', { hasText: k }).first().click(); await page.waitForTimeout(600); }
    check('doi khoang thoi gian khong loi', errors.length === e0);
    await go('/dashboard');
    const dash = await page.locator('.content').innerText();
    const ov = await api('GET', '/reports/overview');
    check('Tong quan: doanh thu hom nay = API overview', JSON.stringify(ov).length > 2 && dash.includes(Number(ov.todayRevenue ?? ov.today?.revenue ?? revenue).toLocaleString('vi-VN')), JSON.stringify(ov).slice(0, 200));
    await page.locator('a:has-text("Bán hàng"), button:has-text("Bán hàng")').last().click(); await page.waitForTimeout(600);
    check('nut "Ban hang" o Tong quan -> man Ban hang', page.url().includes('/sale'));
    noErrors(e0, 'Thong ke');
    await layout('/reports', 'Thong ke');
    await layout('/dashboard', 'Tong quan');
    await layout('/categories', 'Loai hang');
  }

  // ===================== 12. TAI KHOAN + DOI MAT KHAU =====================
  section = 'Tai khoan';
  {
    const e0 = errMark();
    await go('/accounts');
    await page.locator('button:has-text("Thêm tài khoản")').click(); await page.waitForTimeout(300);
    await fill(field('Tên đăng nhập'), 'ztnv'); await fill(field('Họ tên'), 'ZT Nhân viên');
    await dlg().locator('.el-radio', { hasText: 'Nhân viên bán hàng' }).click();
    await fill(field('Mật khẩu ban đầu'), 'ztnv123');
    await dlg().locator('.el-dialog__footer button.el-button--primary').click(); await page.waitForTimeout(700);
    const u = (await api('GET', '/users')).find((x) => x.username === 'ztnv');
    check('them tai khoan nhan vien', u && u.role === 'STAFF' && u.active, JSON.stringify(u));
    // NV dang nhap: chi thay Ban hang; doi mat khau
    const c2 = await browser.newContext({ timezoneId: TZ, viewport: { width: 1366, height: 860 } }); const p2 = await c2.newPage();
    const loginAs = async (p, user, pass) => { await p.goto(UI + '/login'); await p.fill('input[autocomplete=username]', user); await p.fill('input[type=password]', pass); await p.keyboard.press('Enter'); await p.waitForTimeout(1200); return new URL(p.url()).pathname; };
    const where = await loginAs(p2, 'ztnv', 'ztnv123');
    check('NV dang nhap -> vao Ban hang (hoac bat doi mat khau)', ['/sale', '/change-password'].includes(where), where);
    await p2.goto(UI + '/password'); await p2.waitForTimeout(600);
    const pw = (label) => p2.locator(`.el-form-item:has(.el-form-item__label:text-is("${label}")) input`).first();
    const submitPw = async (cur, nw, again) => { await pw('Mật khẩu hiện tại').fill(cur); await pw('Mật khẩu mới').fill(nw); await pw('Nhập lại mật khẩu mới').fill(again); await p2.locator('button[type=submit]').click(); await p2.waitForTimeout(800); };
    await submitPw('sai-cu', 'ztnv456', 'ztnv456');
    check('doi mat khau: sai mat khau hien tai -> bao loi', (await p2.locator('body').innerText()).match(/không đúng|sai/i) != null);
    await submitPw('ztnv123', 'ztnv456', 'ztnv789');
    check('doi mat khau: nhap lai khong khop -> bao loi, chua doi', (await p2.locator('body').innerText()).includes('Nhập lại mật khẩu mới không chính xác'));
    await submitPw('ztnv123', 'ztnv456', 'ztnv456');
    check('doi mat khau thanh cong -> dang nhap bang mat khau moi', ['/sale', '/change-password'].includes(await loginAs(p2, 'ztnv', 'ztnv456')) && (await loginAs(p2, 'ztnv', 'ztnv123')) === '/login');
    // dat lai mat khau + khoa
    await page.locator('.el-table__row', { hasText: 'ztnv' }).locator('button:has-text("Đặt lại mật khẩu")').click(); await page.waitForTimeout(300);
    await page.locator('.el-message-box input').fill('reset999'); await boxConfirm();
    check('quan tri dat lai mat khau -> NV dang nhap duoc bang mat khau moi', (await loginAs(p2, 'ztnv', 'reset999')) !== '/login');
    await page.locator('.el-table__row', { hasText: 'ztnv' }).locator('button:has-text("Sửa")').click(); await page.waitForTimeout(300);
    await dlg().locator('.el-switch').click(); await dlg().locator('.el-dialog__footer button.el-button--primary').click(); await page.waitForTimeout(700);
    check('khoa tai khoan -> khong dang nhap duoc', (await api('GET', '/users')).find((x) => x.username === 'ztnv').active === false && (await loginAs(p2, 'ztnv', 'reset999')) === '/login');
    await c2.close();
    noErrors(e0, 'Tai khoan');
    await layout('/accounts', 'Tai khoan');
    await layout('/password', 'Doi mat khau');
  }

  // ===================== 13. XAC THUC 2 LOP (admin) =====================
  section = 'Xac thuc 2 lop';
  {
    const e0 = errMark();
    const crypto = require('crypto');
    const b32 = (str) => { const A = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'; let bits = 0, v = 0; const out = []; for (const ch of str.replace(/\s|=/g, '').toUpperCase()) { v = (v << 5) | A.indexOf(ch); bits += 5; if (bits >= 8) { out.push((v >>> (bits - 8)) & 255); bits -= 8; } } return Buffer.from(out); };
    // ma 6 so nhu app Google Authenticator (RFC 6238); step: buoc 30 giay (mac dinh: bay gio)
    const totp = (secret, step = Math.floor(Date.now() / 30000)) => { const b = Buffer.alloc(8); b.writeBigUInt64BE(BigInt(step)); const h = crypto.createHmac('sha1', b32(secret)).update(b).digest(); const o = h[h.length - 1] & 15; return String(((h.readUInt32BE(o) & 0x7fffffff) % 1e6)).padStart(6, '0'); };
    await api('POST', '/users', { username: 'ztqt', fullName: 'ZT Quản trị 2', role: 'ADMIN', password: 'ztqt12345' });
    const c3 = await browser.newContext({ timezoneId: TZ, viewport: { width: 1366, height: 860 } }); const p3 = await c3.newPage();
    p3.on('pageerror', (e) => errors.push('JS: ' + e.message));
    const loginUi = async (user, pass, otp) => {
      await p3.goto(UI + '/login'); await p3.fill('input[autocomplete=username]', user); await p3.fill('input[type=password]', pass);
      await p3.keyboard.press('Enter'); await p3.waitForTimeout(900);
      if (otp !== undefined && await p3.locator('input[autocomplete=one-time-code]').count()) { await p3.fill('input[autocomplete=one-time-code]', otp); await p3.keyboard.press('Enter'); await p3.waitForTimeout(900); }
      return new URL(p3.url()).pathname;
    };
    await loginUi('ztqt', 'ztqt12345');
    check('admin chua bat 2 lop -> thay loi nhac "Bat ngay"', (await p3.locator('.tf-nudge').count()) === 1);
    await p3.locator('.tf-nudge a:has-text("Bật ngay")').click(); await p3.waitForTimeout(600);
    await p3.locator('button:has-text("Bật xác thực 2 lớp")').click();
    await p3.fill('#tf-pass', 'ztqt12345'); await p3.locator('.tf button[type=submit]').click(); await p3.waitForTimeout(800);
    check('nhap mat khau -> hien ma QR', await p3.locator('img.qr').isVisible());
    await p3.locator('.manual summary').click();
    const secret = (await p3.locator('.secret').innerText()).replace(/\s/g, '');
    await p3.fill('#tf-code', '000000'); await p3.locator('.tf button[type=submit]').click(); await p3.waitForTimeout(600);
    check('ma sai -> bao loi, chua bat', (await p3.locator('.tf .el-alert--error').count()) === 1 && !(await api('GET', '/users')).find((u) => u.username === 'ztqt').twoFactor);
    const used = totp(secret);
    await p3.fill('#tf-code', used); await p3.locator('.tf button[type=submit]').click(); await p3.waitForTimeout(800);
    const codes = await p3.locator('.codes code').allInnerTexts();
    check('ma dung -> bat, hien 8 ma du phong', codes.length === 8 && (await api('GET', '/users')).find((u) => u.username === 'ztqt').twoFactor, codes.join(' '));
    await p3.locator('button:has-text("Đã lưu xong")').click(); await p3.waitForTimeout(300);
    check('da bat -> het loi nhac', (await p3.locator('.tf-nudge').count()) === 0);
    await p3.evaluate(() => localStorage.clear());
    // dang nhap lai: hoi ma
    await loginUi('ztqt', 'ztqt12345');
    check('dang nhap dung mat khau -> hoi ma 6 so, chua vao', new URL(p3.url()).pathname === '/login' && (await p3.locator('input[autocomplete=one-time-code]').count()) === 1);
    await p3.fill('input[autocomplete=one-time-code]', used); await p3.keyboard.press('Enter'); await p3.waitForTimeout(900);
    check('ma da dung roi -> tu choi', new URL(p3.url()).pathname === '/login' && /không đúng|đã dùng/.test(await p3.locator('.el-alert').innerText()));
    await p3.fill('input[autocomplete=one-time-code]', totp(secret, Math.floor(Date.now() / 30000) + 1)); await p3.keyboard.press('Enter'); await p3.waitForTimeout(900);
    check('ma moi -> vao Tong quan', new URL(p3.url()).pathname === '/dashboard');
    await p3.evaluate(() => localStorage.clear());
    check('ma du phong -> vao duoc', (await loginUi('ztqt', 'ztqt12345', codes[0])) === '/dashboard');
    await p3.evaluate(() => localStorage.clear());
    check('ma du phong dung lai -> khong vao', (await loginUi('ztqt', 'ztqt12345', codes[0])) === '/login');
    await c3.close();
    // quan tri khac tat giup
    await go('/accounts');
    await page.locator('.el-table__row', { hasText: 'ztqt' }).locator('button:has-text("Tắt 2 lớp")').click(); await boxConfirm();
    check('quan tri tat 2 lop giup -> tat', !(await api('GET', '/users')).find((u) => u.username === 'ztqt').twoFactor);
    noErrors(e0, 'Xac thuc 2 lop');
  }

  // ===================== 13. LOAI HANG: xoa bi khoa khi con san pham =====================
  section = 'Loai hang';
  await go('/categories');
  check('nut Xoa loai con san pham bi khoa', await page.locator('.el-table__row', { hasText: 'ZT Tôn' }).locator('button:has-text("Xóa")').isDisabled());

  await browser.close();
  const failed = results.filter((r) => !r.ok);
  const bySection = {};
  for (const r of results) { const s = (bySection[r.section] ??= { pass: 0, fail: 0 }); r.ok ? s.pass++ : s.fail++; }
  console.log('\n' + Object.entries(bySection).map(([k, v]) => `${k.padEnd(14)} ${v.pass}/${v.pass + v.fail}`).join('\n'));
  console.log(`\n${results.length - failed.length}/${results.length} PASS`);
  process.exit(failed.length ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(2); });
