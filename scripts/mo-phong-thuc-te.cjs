/*
 * MO PHONG THUC TE: du lieu ngau nhien (co seed) chay qua GIAO DIEN that, do cong suc thao tac + doi chieu so lieu.
 * Can: backend chay voi du lieu mau (SEED_DEMO=true, DB trong) tren :8090, frontend dev :5173, Playwright, pdftotext.
 *   SEED=7 SALES=40 node scripts/mo-phong-thuc-te.cjs     -> in bao cao + ghi JSON ket qua (OUT=duong-dan.json)
 * KHONG chay tren DB that: script tao hoa don, thu no, nhap kho...
 */
const { chromium } = require('playwright');
const fs = require('fs');

const API = (process.env.API_URL ?? 'http://localhost:8090') + '/api';
const UI = process.env.UI_URL ?? 'http://localhost:5173';
const SALES = Number(process.env.SALES ?? 40);
let seed = Number(process.env.SEED ?? 7);
const rnd = () => ((seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648);
const pick = (a) => a[Math.floor(rnd() * a.length)];
const chance = (p) => rnd() < p;
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const fold = (s) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
const N = Number;
const sum = (a) => a.reduce((x, y) => x + y, 0);
const vn = (n) => Number(n).toLocaleString('vi-VN');

// ---------- ghi nhan ----------
const log = { sales: [], admin: [], checks: [], issues: [], search: [], perf: [], layout: [] };
function check(name, ok, detail = '') { log.checks.push({ name, ok: !!ok, detail }); if (!ok) console.log(`FAIL  ${name}  — ${detail}`); }
function issue(kind, text, data) { log.issues.push({ kind, text, data }); }

let token;
async function api(method, p, body, tok = token) {
  const r = await fetch(API + p, { method, headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + tok }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text(); let d = null; try { d = t ? JSON.parse(t) : null; } catch { d = t; }
  if (r.status >= 300) throw new Error(`${method} ${p} -> ${r.status} ${t.slice(0, 200)}`);
  return d;
}
async function all(p) {
  const out = [];
  for (let page = 0; ; page++) {
    const r = await api('GET', `${p}${p.includes('?') ? '&' : '?'}page=${page}&size=100`);
    if (Array.isArray(r)) return r;
    out.push(...r.items); if (out.length >= r.total || !r.items.length) return out;
  }
}

// ---------- tinh doc lap (BigInt) ----------
const scaled = (s, k) => { const [i, f = ''] = String(s).replace(',', '.').split('.'); return BigInt(i + f.padEnd(k, '0').slice(0, k)); };
const hev = (n, d) => { const q = n / d, r = n % d; if (r * 2n > d) return q + 1n; if (r * 2n < d) return q; return q % 2n === 0n ? q : q + 1n; };
function exactLine(qty, sheets, factor, price) {
  let f = scaled(factor, 4); if (sheets > 0 && f === 0n) f = 10800n;
  const q = scaled(qty, 3), raw = sheets === 0 && f === 0n ? q * 10000n : q * BigInt(sheets) * f;
  return { totalQty: N(hev(raw, 10000n)) / 1000, lineTotal: N(hev(raw * BigInt(price), 10000000n)) };
}

// ---------- page + bo dem thao tac ----------
function instrument(page) {
  const c = { keys: 0, clicks: 0 };
  const kb = page.keyboard;
  const u = {
    c,
    reset() { c.keys = 0; c.clicks = 0; },
    async type(t) { c.keys += [...t].length; await kb.type(t, { delay: 8 }); },
    async press(k) { c.keys += 1; await kb.press(k); },
    async click(sel) { c.clicks += 1; await page.click(sel); },
    async fill(sel, t) { c.clicks += 1; c.keys += [...t].length; await page.click(sel); await kb.press('Control+A'); await kb.type(t, { delay: 8 }); },
  };
  return u;
}
async function watchMessages(page) {
  await page.evaluate(() => {
    window.__msgs = [];
    new MutationObserver((ms) => ms.forEach((m) => m.addedNodes.forEach((n) => {
      if (n.nodeType === 1 && n.classList?.contains('el-message')) window.__msgs.push({ type: [...n.classList].find((x) => /--(warning|error|success|info)/.test(x)) ?? '', text: n.innerText });
    }))).observe(document.body, { childList: true, subtree: true });
  });
}
const takeMsgs = (page) => page.evaluate(() => { const m = window.__msgs ?? []; window.__msgs = []; return m; });

async function login(page, user, pass) {
  await page.goto(UI + '/login'); await page.fill('input[autocomplete=username]', user); await page.fill('input[type=password]', pass);
  await page.keyboard.press('Enter'); await page.waitForURL((u) => !u.pathname.includes('/login'));
}

// ---------- sinh cau tim nhu nguoi ban hang ----------
function queriesFor(name) {
  const w = fold(name).split(/\s+/);
  const nums = w.filter((x) => /\d/.test(x));
  const words = w.filter((x) => !/\d/.test(x));
  const q = [];
  q.push({ kind: 'co dau, 2 tu dau', q: name.split(/\s+/).slice(0, 2).join(' ') });
  q.push({ kind: 'khong dau, 2 tu dau', q: words.slice(0, 2).join(' ') });
  if (nums.length) {
    q.push({ kind: 'loai + quy cach (nhu ten)', q: `${words[0]} ${nums[0]}` });
    const n = nums[0].replace(/mm$/, '');
    const comma = n.replace('.', ',').replace(/,?0+$/, '');
    if (comma !== nums[0]) q.push({ kind: 'loai + quy cach go kieu VN (0,4 thay 0.40mm)', q: `${words[0]} ${comma}` });
    if (/x/.test(nums[0])) q.push({ kind: 'quy cach bo phan day (40x80)', q: `${words[0]} ${nums[0].split('x').slice(0, 2).join('x')}` });
  }
  if (words.length > 2) q.push({ kind: 'tu dac trung (mau / chat lieu)', q: `${words[0]} ${words[words.length - 1]}` });
  const t = words.find((x) => x.length >= 4);
  if (t) q.push({ kind: 'go sai 1 chu', q: t.slice(0, 1) + t.slice(2) });
  return q;
}

(async () => {
  const t0 = Date.now();
  token = (await (await fetch(API + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: 'admin123' }) })).json()).token;
  const products = await all('/products');
  let customers = await all('/customers');
  if (products.length < 10) throw new Error('Can du lieu mau: chay backend voi SEED_DEMO=true tren DB trong');
  const sheet = products.filter((p) => /tôn|úp nóc|máng/i.test(p.name) && p.unit === 'm');
  try { await api('POST', '/users', { username: 'nvsim', fullName: 'Nguyễn Thị Thu', role: 'STAFF', password: 'nvsim123' }); } catch { /* da co */ }

  // ===== 1. DO TIM KIEM (API, khong qua giao dien) =====
  for (const p of products) for (const { kind, q } of queriesFor(p.name)) {
    const t = Date.now();
    const r = await api('GET', `/products?q=${encodeURIComponent(q)}&size=30`);
    const ms = Date.now() - t;
    const rank = r.items.findIndex((x) => x.id === p.id);
    log.search.push({ product: p.name, kind, q, rank, total: r.total, ms });
  }

  // ===== 2. BAN HANG QUA GIAO DIEN =====
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1366, height: 768 } });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push('JS: ' + e.message));
  page.on('response', (r) => { if (r.url().includes('/api/') && r.status() >= 500) errors.push(`${r.status()} ${r.url().split('/api')[1]}`); });
  const u = instrument(page);

  const searchPick = async (q, wantName, focus) => {
    // go cau tim, cho ket qua cua dung cau do, tim vi tri san pham muon; khong co -> null
    const resp = page.waitForResponse((r) => { const x = new URL(r.url()); return x.pathname === '/api/products' && x.searchParams.get('q') === q; }, { timeout: 8000 }).catch(() => null);
    await focus(); await u.press('Control+A'); await u.type(q);
    await resp; await page.waitForTimeout(120);
    const opts = await page.evaluate(() => [...document.querySelectorAll('.el-select-dropdown__item .opt-name')].filter((e) => e.offsetParent).map((e) => e.innerText.split(' · ')[0]));
    return opts.indexOf(wantName);
  };

  for (const who of ['staff', 'admin']) {
    await ctx.clearCookies();
    await login(page, who === 'staff' ? 'nvsim' : 'admin', who === 'staff' ? 'nvsim123' : 'admin123');
    const n = who === 'staff' ? Math.round(SALES * 0.7) : SALES - Math.round(SALES * 0.7);
    for (let s = 0; s < n; s++) {
      await page.goto(UI + '/sale'); await page.waitForSelector('#pick-product'); await page.waitForTimeout(300);
      await watchMessages(page);
      const draftId = await page.evaluate(() => sessionStorage.getItem('sale.draftId'));
      u.reset(); const ts = Date.now();
      const sale = { who, lines: [], notes: [], searchMiss: 0, arrowPresses: 0, reSearch: 0 };
      // --- chon hang: nhom theo san pham, ton thuong nhieu doan
      const groups = [];
      for (let g = 0, ng = int(1, 4); g < ng; g++) {
        const p = chance(0.55) ? pick(sheet) : pick(products);
        const isSheet = sheet.includes(p);
        const segs = isSheet ? int(1, 5) : 1;
        groups.push({ p, isSheet, segs });
      }
      let prevProduct = null;
      let curPrice = 0; // gia dang hien o o Don gia (giu sang doan sau cung san pham, ke ca gia da sua)
      for (const g of groups) {
        // tim san pham (neu khac san pham dang giu)
        if (prevProduct !== g.p.id) {
          const qs = queriesFor(g.p.name);
          const qq = pick(qs.filter((x) => x.kind !== 'go sai 1 chu'));
          let idx = await searchPick(qq.q, g.p.name, () => u.press('F2'));
          if (idx < 0) { sale.searchMiss++; sale.notes.push(`tim "${qq.q}" khong ra ${g.p.name}`); idx = await searchPick(g.p.name, g.p.name, () => u.press('F2')); sale.reSearch++; }
          for (let k = 0; k < idx; k++) { await u.press('ArrowDown'); sale.arrowPresses++; }
          await u.press('Enter'); await page.waitForTimeout(250);
          const priceShown = N(await page.inputValue('#p'));
          if (priceShown !== N(g.p.price)) check(`chon dung san pham ${g.p.name}`, false, `gia hien ${priceShown}`);
          curPrice = N(g.p.price);
        } else {
          sale.notes.push('giu san pham, khong tim lai');
        }
        const fresh = prevProduct !== g.p.id;
        prevProduct = g.p.id;
        // ton: phan lon go 1 lan "4,7x2 4,5x3 3" o o Nhieu doan (con tro dang o do sau khi chon); con lai Enter de nhap tung dong
        if (g.isSheet && chance(0.75)) {
          if (!fresh) await page.focus('#segs');
          const segs = [];
          for (let k = 0; k < g.segs; k++) {
            const len = (int(150, 600) / 100).toFixed(chance(0.5) ? 1 : 2).replace(/0$/, '');
            const sheets = int(1, 20);
            segs.push({ product: g.p.name, price: curPrice, qty: len, sheets, factor: '1.08' });
          }
          await u.type(segs.map((x) => `${x.qty.replace('.', ',')}x${x.sheets}`).join(' '));
          await u.press('Enter'); await page.waitForTimeout(350);
          for (const line of segs) { Object.assign(line, exactLine(line.qty, line.sheets, '1.08', line.price)); sale.lines.push(line); }
          sale.multi = (sale.multi ?? 0) + 1;
          continue;
        }
        if ((await page.evaluate(() => document.activeElement?.id)) === 'segs') await u.press('Enter'); // o Nhieu doan trong + Enter -> sang So luong
        for (let k = 0; k < g.segs; k++) {
          const line = { product: g.p.name, price: curPrice };
          if (g.isSheet) {
            const len = (int(150, 600) / 100).toFixed(chance(0.5) ? 1 : 2).replace(/0$/, '');
            line.qty = len; line.sheets = int(1, 20); line.factor = chance(0.1) ? '1,05' : '';
          } else {
            line.qty = g.p.unit === 'kg' && chance(0.6) ? (int(10, 400) / 10).toString() : String(int(1, 30));
            line.sheets = 0; line.factor = '';
          }
          if (chance(0.12)) { line.price = Math.round(N(g.p.price) * (0.9 + rnd() * 0.15) / 1000) * 1000; if (line.price !== curPrice) sale.notes.push('sua gia -> doan sau giu gia da sua'); }
          // o So luong dang co con tro (sau chon / sau them)
          await u.type(line.qty.replace('.', ',')); await u.press('Tab');
          if (line.sheets) { await u.type(String(line.sheets)); }
          await u.press('Tab');
          if (line.factor) await u.type(line.factor);
          await u.press('Tab');
          if (line.price !== curPrice) { await u.type(vn(line.price)); curPrice = line.price; } // go co dau cham "92.000"
          // bay: lo bam Enter 2 lan
          await u.press('Enter'); await page.waitForTimeout(300);
          if (chance(0.08)) { await u.press('Enter'); await page.waitForTimeout(250); sale.notes.push('lo bam Enter 2 lan'); }
          const ex = exactLine(line.qty, line.sheets, line.factor || '0', line.price);
          Object.assign(line, ex);
          sale.lines.push(line);
        }
      }
      // sua 1 dong / xoa 1 dong
      let order = await api('GET', `/orders/${draftId}`);
      if (order.items.length !== sale.lines.length) check(`ban ${who}#${s}: so dong da luu`, false, `${order.items.length} vs ${sale.lines.length}`);
      if (chance(0.12) && order.items.length > 1) {
        const i = int(0, order.items.length - 1);
        for (const ic of await page.$$('.group-row .el-table__expand-icon:not(.el-table__expand-icon--expanded)')) { await ic.click(); await page.waitForTimeout(150); }
        await u.click(`.row-actions[data-item="${order.items[i].id}"] button:nth-child(2)`); await page.waitForTimeout(250);
        await u.click('.el-message-box__btns .el-button--primary'); await page.waitForTimeout(400);
        sale.lines.splice(i, 1); sale.notes.push('xoa 1 dong');
      }
      // khach
      const walkIn = chance(0.35);
      let cust = null;
      if (walkIn) {
        await u.fill('#walkin-name', pick(['Anh Ba', 'Chị Hai', 'Khách lẻ', 'Anh Tư xe ôm', 'Cô Năm']));
      } else {
        cust = pick(customers);
        const q = chance(0.5) ? fold(cust.name).split(' ').slice(-2).join(' ') : (cust.phone ?? fold(cust.name)).slice(-4);
        const r = page.waitForResponse((x) => x.url().includes('/api/customers?'), { timeout: 8000 }).catch(() => null);
        await u.press('F4'); await u.type(q); await r; await page.waitForTimeout(150);
        const opts = await page.evaluate(() => [...document.querySelectorAll('.el-select-dropdown__item')].filter((e) => e.offsetParent).map((e) => e.innerText.split('\n')[0].trim()));
        const idx = opts.findIndex((o) => o.startsWith(cust.name));
        if (idx < 0) { sale.notes.push(`tim khach "${q}" khong ra ${cust.name}`); sale.searchMiss++; }
        for (let k = 0; k < Math.max(0, idx); k++) { await u.press('ArrowDown'); sale.arrowPresses++; }
        await u.press('Enter'); await page.waitForTimeout(300);
        cust = (await api('GET', `/customers/${cust.id}`));
      }
      // tien
      order = await api('GET', `/orders/${draftId}`);
      const items = N(order.itemsTotal);
      const ship = chance(0.15) ? int(1, 6) * 50000 : 0;
      if (ship) await u.fill('#ship', vn(ship));
      const old = cust ? N(cust.debt) : 0;
      const payable = items + ship + old;
      let paid;
      const mode = walkIn ? pick(['du', 'du', 'tron', 'thieu-roi-sua']) : pick(['du', 'hang', 'mot-phan', 'ghi-no', 'thua']);
      if (mode === 'du') { await u.click('.quick >> text=Trả đủ'); paid = payable; }
      else if (mode === 'tron') { paid = Math.ceil(payable / 50000) * 50000; await u.fill('#paid', vn(paid)); }
      else if (mode === 'thieu-roi-sua') {
        await u.fill('#paid', vn(Math.max(0, payable - 20000)));
        const blocked = await page.evaluate(() => document.querySelector('.cta')?.disabled);
        if (!blocked) check('khach le tra thieu bi chan', false);
        await u.click('.quick >> text=Trả đủ'); paid = payable; sale.notes.push('khach le tra thieu -> bi chan, bam Tra du');
      }
      else if (mode === 'hang') { if (old > 0) { await u.click('.quick >> text=Trả tiền hàng'); paid = items + ship; } else { await u.click('.quick >> text=Trả đủ'); paid = payable; } }
      else if (mode === 'mot-phan') { paid = Math.round(payable * (0.2 + rnd() * 0.6) / 1000) * 1000; await u.fill('#paid', vn(paid)); }
      else if (mode === 'ghi-no') { await u.click('.quick >> text=Ghi nợ hết'); paid = 0; }
      else { paid = payable + int(1, 9) * 10000; await u.fill('#paid', vn(paid)); }
      // chot: F9 roi Enter (ban phim) hoac bam nut
      if (chance(0.5)) { await u.press('F9'); await u.press('Enter'); } else await u.click('.cta');
      await page.waitForTimeout(700);
      const msgs = await takeMsgs(page);
      const done = await api('GET', `/orders/${draftId}`);
      const ms = Date.now() - ts;
      const expItems = sum(sale.lines.map((l) => l.lineTotal));
      check(`ban ${who}#${s}: hoa don da chot`, done.status === 'PAID', `${done.status} ${msgs.map((m) => m.text).join(' | ')}`);
      check(`ban ${who}#${s}: tien hang = tu tinh`, N(done.itemsTotal) === expItems, `${done.itemsTotal} vs ${expItems}`);
      done.items.forEach((it, i) => { const l = sale.lines[i];
        if (!l || N(it.lineTotal) !== l.lineTotal || N(it.totalQuantity) !== l.totalQty || N(it.unitPrice) !== l.price)
          check(`ban ${who}#${s} dong ${i + 1}`, false, `server ${it.productName} SL ${it.quantity} tam ${it.sheets} hs ${it.factor} gia ${it.unitPrice} -> ${it.totalQuantity} / ${it.lineTotal}; mong doi ${l?.product} ${l?.qty} x ${l?.sheets} x ${l?.factor || 'mac dinh'} gia ${l?.price} -> ${l?.totalQty} / ${l?.lineTotal}`); });
      check(`ban ${who}#${s}: tong phai tra`, N(done.totalPayable) === items + ship + old, `${done.totalPayable} vs ${items + ship + old}`);
      check(`ban ${who}#${s}: con no`, N(done.debtAfter) === Math.max(0, payable - paid), `${done.debtAfter} vs ${Math.max(0, payable - paid)}`);
      if (cust) check(`ban ${who}#${s}: no khach sau don`, N((await api('GET', `/customers/${cust.id}`)).debt) === N(done.debtAfter));
      const nLines = sale.lines.length;
      log.sales.push({ ...sale, walkIn, mode, ship, nLines, keys: u.c.keys, clicks: u.c.clicks, ms, msgs: msgs.filter((m) => !/success/.test(m.type)), code: done.code });
      customers = await all('/customers');
    }
  }

  // ===== 3. QUAN TRI: thu no, huy phieu, dieu chinh, huy don =====
  await ctx.clearCookies(); await login(page, 'admin', 'admin123'); await watchMessages(page);
  const inDebt = () => customers.filter((c) => N(c.debt) > 0);
  for (let k = 0; k < 8 && inDebt().length; k++) {
    const c = pick(inDebt());
    u.reset(); const ts = Date.now();
    await page.goto(`${UI}/customers/${c.id}`); await page.waitForSelector('#amt', { timeout: 8000 });
    const amt = chance(0.3) ? N(c.debt) : Math.round(N(c.debt) * (0.1 + rnd() * 0.6) / 1000) * 1000;
    await u.fill('#amt', vn(amt));
    if (chance(0.4)) await u.click('.pay >> text=Chuyển khoản');
    await u.click('.pay button[type=submit]'); await page.waitForTimeout(600);
    const after = await api('GET', `/customers/${c.id}`);
    check(`thu no ${c.name}`, N(after.debt) === N(c.debt) - amt, `${after.debt} vs ${N(c.debt) - amt}`);
    const pr = page.waitForResponse((r) => r.url().includes('/receipt.pdf'), { timeout: 8000 }).catch(() => null);
    await u.click('.el-dialog >> text=In phiếu thu'); const resp = await pr;
    check(`in phieu thu ${c.name}`, resp && resp.status() === 200);
    log.admin.push({ op: 'thu no + in phieu', keys: u.c.keys, clicks: u.c.clicks, ms: Date.now() - ts });
    customers = await all('/customers');
  }
  // huy 1 phieu thu, dieu chinh 1 lan
  { const c = pick(customers.filter((x) => x.id)); const led = (await api('GET', `/customers/${c.id}/debt-ledger?size=50`)).items; const r = led.find((x) => x.canVoid);
    if (r) { u.reset(); const ts = Date.now();
      await page.goto(`${UI}/customers/${c.id}`); await page.waitForTimeout(800);
      await u.click(`button[aria-label="Hủy phiếu ${r.receiptNo}"]`); await u.fill('.el-message-box input', 'Thu nhầm số tiền'); await u.click('.el-message-box__btns .el-button--danger');
      await page.waitForTimeout(600); log.admin.push({ op: 'huy phieu thu', keys: u.c.keys, clicks: u.c.clicks, ms: Date.now() - ts }); } }
  // huy hoa don ngau nhien (ca hoa don khach da tra no)
  const recent = (await api('GET', '/orders?status=PAID&size=30')).items.filter((o) => o.customerId);
  for (let k = 0; k < 3 && recent.length; k++) {
    const o = recent.splice(int(0, recent.length - 1), 1)[0];
    const c0 = await api('GET', `/customers/${o.customerId}`); const pv = await api('GET', `/orders/${o.id}/cancel-preview`);
    u.reset(); const ts = Date.now();
    await page.goto(`${UI}/orders/${o.id}`); await page.waitForSelector('.el-drawer', { timeout: 8000 }); await page.waitForTimeout(500);
    await u.click('.el-drawer >> text=Hủy hóa đơn'); await page.waitForTimeout(400); await u.click('.el-message-box__btns .el-button--danger'); await page.waitForTimeout(700);
    if (N(pv.refund) > 0) { await u.click('.el-message-box >> text=Đã trả tiền cho khách'); await page.waitForTimeout(300); }
    const c1 = await api('GET', `/customers/${o.customerId}`); const o1 = await api('GET', `/orders/${o.id}`);
    check(`huy ${o.code}: no = truoc - tru`, N(c1.debt) === N(c0.debt) - N(pv.debtReversal) && N(c1.debt) >= 0, `${c0.debt} -> ${c1.debt}`);
    check(`huy ${o.code}: luu tien tra lai`, N(o1.refundAmount) === N(pv.refund));
    log.admin.push({ op: 'huy hoa don', keys: u.c.keys, clicks: u.c.clicks, ms: Date.now() - ts, refund: pv.refund });
  }

  // ===== 4. KHO: nhap hang qua giao dien (co don vi quy doi), kiem tra ton tang dung =====
  for (let k = 0; k < 2; k++) {
    const lines = [pick(sheet), pick(products.filter((p) => !sheet.includes(p)))];
    const before = {}; for (const p of lines) before[p.id] = N((await api('GET', `/stock/${p.id}`)).stock.onHand);
    u.reset(); const ts = Date.now();
    await page.goto(UI + '/receipts/new'); await page.waitForTimeout(900);
    const sups = (await api('GET', '/suppliers?size=20')).items;
    // man Nhap hang: Enter KHONG chon ket qua dau (khac man Ban hang) -> phai mui ten xuong roi Enter
    if (sups.length) { await u.click('#sup'); await u.type(fold(sups[0].name).split(' ')[0]); await page.waitForTimeout(700); await u.press('ArrowDown'); await u.press('Enter'); await page.waitForTimeout(200); }
    const added = [];
    for (let i = 0; i < lines.length; i++) {
      const p = lines[i];
      const rows = await page.$$('.lines .el-table__body tr');
      if (rows.length <= i) { await u.click('text=Thêm dòng'); await page.waitForTimeout(200); }
      const rowSel = `.lines .el-table__body tr:nth-child(${i + 1})`;
      const r = page.waitForResponse((x) => x.url().includes('/api/products?'), { timeout: 8000 }).catch(() => null);
      await u.click(`${rowSel} .el-select`); await u.type(fold(p.name).split(' ').slice(0, 2).join(' ')); await r; await page.waitForTimeout(200);
      const opts = await page.evaluate(() => [...document.querySelectorAll('.el-select-dropdown__item')].filter((e) => e.offsetParent).map((e) => e.innerText));
      const idx = opts.findIndex((o) => o.startsWith(p.name)); for (let a = 0; a <= Math.max(0, idx); a++) await u.press('ArrowDown');
      await u.press('Enter'); await page.waitForTimeout(500);
      const qty = p.unit === 'm' ? int(100, 400) : int(10, 60);
      const price = Math.round(N(p.price) * 0.8 / 1000) * 1000;
      await u.fill(`${rowSel} input[aria-label="Số lượng nhập"]`, String(qty));
      await u.fill(`${rowSel} input[aria-label="Giá nhập"]`, vn(price));
      added.push({ p, qty });
    }
    await u.click('text=Trả đủ');
    await u.click('text=Ghi sổ, nhập kho'); await page.waitForTimeout(500);
    if (await page.isVisible('.el-message-box')) { await u.click('.el-message-box__btns .el-button--primary'); await page.waitForTimeout(800); }
    const msgs = await takeMsgs(page);
    for (const a of added) {
      const now = N((await api('GET', `/stock/${a.p.id}`)).stock.onHand);
      check(`nhap kho ${a.p.name}: ton tang ${a.qty}`, Math.abs(now - before[a.p.id] - a.qty) < 0.0005, `${before[a.p.id]} -> ${now}; ${msgs.map((m) => m.text).join(' | ')}`);
    }
    log.admin.push({ op: 'lap phieu nhap 2 dong + ghi so', keys: u.c.keys, clicks: u.c.clicks, ms: Date.now() - ts, msgs });
  }

  // ===== 5. DO TOC DO =====
  const timeIt = async (label, p, n = 8) => {
    const ts = [];
    for (let i = 0; i < n; i++) { const t = Date.now(); await api('GET', p); ts.push(Date.now() - t); }
    ts.sort((a, b) => a - b);
    log.perf.push({ label, p50: ts[Math.floor(n / 2)], max: ts[n - 1] });
  };
  await timeIt('tim san pham "ton"', '/products?q=ton&size=30');
  await timeIt('tim khach "anh"', '/customers?q=anh&size=20');
  await timeIt('danh sach hoa don trang 1', '/orders?status=PAID&size=20');
  await timeIt('tong quan', '/reports/overview');
  await timeIt('so no 1 khach', `/customers/${customers[0].id}/debt-ledger?size=20`);
  for (const [label, path, sel] of [['Ban hang', '/sale', '#pick-product'], ['Hoa don', '/orders', '.el-table__row'], ['Khach hang', '/customers', '.el-table__row'],
    ['Tong quan', '/dashboard', '.stat'], ['Thong ke', '/reports', '.stat'], ['Ton kho', '/inventory', '.el-table__row']]) {
    const t = Date.now(); await page.goto(UI + path); await page.waitForSelector(sel, { timeout: 15000 }).catch(() => {});
    log.perf.push({ label: `mo man ${label}`, p50: Date.now() - t, max: Date.now() - t });
  }

  // ===== 6. BO CUC man Ban hang o cac co man hinh =====
  for (const [w, h, label] of [[1366, 768, 'laptop 1366x768'], [1280, 720, 'laptop 1280x720'], [1024, 768, 'may tinh bang ngang'], [768, 1024, 'may tinh bang doc'], [390, 844, 'dien thoai']]) {
    await page.setViewportSize({ width: w, height: h });
    await page.goto(UI + '/sale'); await page.waitForSelector('#pick-product'); await page.waitForTimeout(500);
    const info = await page.evaluate(() => {
      const vis = (sel) => { const e = document.querySelector(sel); if (!e) return null; const r = e.getBoundingClientRect(); return { top: Math.round(r.top), bottom: Math.round(r.bottom), inView: r.top >= 0 && r.bottom <= innerHeight }; };
      return { overflowX: document.documentElement.scrollWidth - innerWidth, cta: vis('.cta'), add: vis('.add'), paybar: vis('.paybar'), qty: vis('#q') };
    });
    log.layout.push({ label, ...info });
    await page.screenshot({ path: `${process.env.SHOT_DIR ?? '.'}/sim-sale-${w}x${h}.png` }).catch(() => {});
  }
  await browser.close();

  // ===== 7. BAT BIEN SAU MO PHONG =====
  customers = await all('/customers');
  for (const c of customers) {
    const led = await all(`/customers/${c.id}/debt-ledger`);
    const s = sum(led.map((l) => N(l.increase ?? 0) - N(l.decrease ?? 0)));
    check(`so cai khop no: ${c.name}`, Math.abs(s - N(c.debt)) < 0.01 && N(c.debt) >= 0, `${s} vs ${c.debt}`);
  }
  for (const p of products) {
    const st = (await api('GET', `/stock/${p.id}`)).stock;
    const mv = await all(`/stock/${p.id}/movements`);
    const s = sum(mv.map((m) => N(m.qty)));
    check(`so kho khop ton: ${p.name}`, Math.abs(s - N(st.onHand)) < 0.0005, `${s} vs ${st.onHand}`);
  }
  log.errors = errors;
  log.elapsedSec = Math.round((Date.now() - t0) / 1000);
  if (process.env.OUT) fs.writeFileSync(process.env.OUT, JSON.stringify(log, null, 1));
  const f = log.checks.filter((c) => !c.ok);
  console.log(`\nKiem tra so lieu: ${log.checks.length - f.length}/${log.checks.length} dung; loi JS/5xx: ${errors.length}; ${log.elapsedSec}s`);
})().catch((e) => { console.error(e); process.exit(2); });
