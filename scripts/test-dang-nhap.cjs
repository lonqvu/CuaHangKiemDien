/*
 * Kiem thu DANG NHAP qua giao dien that: ghi nho dang nhap (7 ngay / 8 gio), nho ten dang nhap, "khong hoi ma 2 lop tren may nay",
 * dang xuat moi thiet bi khac, doi mat khau / quan tri dat lai -> may khac bi dang xuat. Moi "may" = 1 trinh duyet rieng.
 *
 * Chay giong cac script khac (backend H2 TRONG tren :8090, frontend dev :5173):
 *   node scripts/test-dang-nhap.cjs
 */
const { chromium } = require('playwright');
const crypto = require('crypto');

const API = (process.env.API_URL ?? 'http://localhost:8090') + '/api';
const UI = process.env.UI_URL ?? 'http://localhost:5173';
const results = [];
function check(name, ok, detail = '') {
  results.push({ name, ok: !!ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail && !ok ? '  — ' + detail : ''}`);
}
async function api(method, p, body, token) {
  const r = await fetch(API + p, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  if (r.status >= 300) throw new Error(`${method} ${p} -> ${r.status} ${t}`);
  return t ? JSON.parse(t) : null;
}
const b32 = (str) => { const A = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'; let bits = 0, v = 0; const out = []; for (const ch of str.toUpperCase()) { v = (v << 5) | A.indexOf(ch); bits += 5; if (bits >= 8) { out.push((v >>> (bits - 8)) & 255); bits -= 8; } } return Buffer.from(out); };
const step = () => Math.floor(Date.now() / 30000);
const totp = (secret, s = step()) => { const b = Buffer.alloc(8); b.writeBigUInt64BE(BigInt(s)); const h = crypto.createHmac('sha1', b32(secret)).update(b).digest(); const o = h[h.length - 1] & 15; return String(((h.readUInt32BE(o) & 0x7fffffff) % 1e6)).padStart(6, '0'); };
const lifetimeHours = (jwt) => { const p = JSON.parse(Buffer.from(jwt.split('.')[1], 'base64url').toString()); return (p.exp - p.iat) / 3600; };

(async () => {
  const admin = (await api('POST', '/auth/login', { username: 'admin', password: 'admin123' })).token;
  const sfx = Date.now().toString(36).slice(-4);
  const NV = 'dnnv' + sfx, QT = 'dnqt' + sfx;
  await api('POST', '/users', { username: NV, fullName: 'Nhân viên thử', role: 'STAFF', password: 'matkhau123' }, admin);
  await api('POST', '/users', { username: QT, fullName: 'Quản trị thử', role: 'ADMIN', password: 'matkhau123' }, admin);

  const browser = await chromium.launch();
  const errors = [];
  const device = async () => {
    const ctx = await browser.newContext({ viewport: { width: 1366, height: 860 } });
    const p = await ctx.newPage();
    p.on('pageerror', (e) => errors.push(e.message));
    p.token = () => p.evaluate(() => localStorage.getItem('token'));
    p.path = () => new URL(p.url()).pathname;
    return p;
  };
  /** Dien form dang nhap; remember / trust = tich hay khong; otp: nhap neu duoc hoi. Tra ve duong dan sau cung. */
  const login = async (p, user, pass, { remember = false, otp, trust = false } = {}) => {
    await p.goto(UI + '/login'); await p.waitForTimeout(400);
    await p.fill('input[autocomplete=username]', user); await p.fill('input[type=password]', pass);
    const cb = p.locator('.remember input[type=checkbox]');
    if ((await cb.isChecked()) !== remember) await p.locator('.remember').click();
    await p.keyboard.press('Enter'); await p.waitForTimeout(900);
    if (await p.locator('input[autocomplete=one-time-code]').count()) {
      if (otp === undefined) return 'otp';
      const t = p.locator('.opt:has-text("Không hỏi mã") input[type=checkbox]');
      if ((await t.isChecked()) !== trust) await p.locator('.opt:has-text("Không hỏi mã")').click();
      await p.fill('input[autocomplete=one-time-code]', otp); await p.keyboard.press('Enter'); await p.waitForTimeout(900);
    }
    return p.path();
  };
  const logout = async (p) => { await p.locator('text=Đăng xuất').last().click(); await p.waitForTimeout(600); };
  const kickedOut = async (p) => { await p.goto(UI + '/sale'); await p.waitForTimeout(1200); return p.path() === '/login'; };

  // ===== 1. Ghi nho dang nhap + nho ten =====
  const may1 = await device();
  await login(may1, NV, 'matkhau123', { remember: false });
  check('khong tich "Ghi nho" -> phien 8 gio', lifetimeHours(await may1.token()) === 8, String(lifetimeHours(await may1.token())));
  await logout(may1); await may1.goto(UI + '/login'); await may1.waitForTimeout(400);
  check('khong tich -> lan sau khong dien san ten', (await may1.inputValue('input[autocomplete=username]')) === '');
  await login(may1, NV, 'matkhau123', { remember: true });
  check('tich "Ghi nho" -> phien 7 ngay', lifetimeHours(await may1.token()) === 168, String(lifetimeHours(await may1.token())));
  await may1.close().catch(() => {});
  const may1b = await (async () => { const p = await may1.context().newPage(); p.token = () => p.evaluate(() => localStorage.getItem('token')); p.path = () => new URL(p.url()).pathname; return p; })();
  await may1b.goto(UI + '/sale'); await may1b.waitForTimeout(900);
  check('dong tab / mo lai -> van dang nhap', may1b.path() === '/sale');
  await logout(may1b); await may1b.goto(UI + '/login'); await may1b.waitForTimeout(500);
  check('dang xuat -> lan sau dien san ten, da tich "Ghi nho", con tro o mat khau',
    (await may1b.inputValue('input[autocomplete=username]')) === NV && await may1b.locator('.remember input[type=checkbox]').isChecked()
    && await may1b.evaluate(() => document.activeElement?.getAttribute('type') === 'password'));
  check('khong luu mat khau tren may', !(await may1b.evaluate(() => JSON.stringify(localStorage))).includes('matkhau123'));
  await login(may1b, NV, 'matkhau123', { remember: false });
  await logout(may1b); await may1b.goto(UI + '/login'); await may1b.waitForTimeout(400);
  check('bo tich "Ghi nho" -> quen ten', (await may1b.inputValue('input[autocomplete=username]')) === '');

  // ===== 2. Dang xuat moi thiet bi khac (nhan vien) =====
  const dt = await device(), pc = await device();
  await login(dt, NV, 'matkhau123', { remember: true });
  await login(pc, NV, 'matkhau123');
  await pc.goto(UI + '/password'); await pc.waitForTimeout(600);
  await pc.locator('button:has-text("Đăng xuất mọi thiết bị khác")').click(); await pc.locator('.el-message-box__btns .el-button--primary').click(); await pc.waitForTimeout(800);
  check('NV bam "Dang xuat moi thiet bi khac" -> dien thoai bi dang xuat', await kickedOut(dt));
  await pc.goto(UI + '/sale'); await pc.waitForTimeout(900);
  check('... may dang dung van dang nhap', pc.path() === '/sale');

  // ===== 3. Doi mat khau -> may khac ra, may dang doi o lai =====
  await login(dt, NV, 'matkhau123', { remember: true });
  await pc.goto(UI + '/password'); await pc.waitForTimeout(600);
  const pw = (label) => pc.locator(`.el-form-item:has(.el-form-item__label:text-is("${label}")) input`).first();
  await pw('Mật khẩu hiện tại').fill('matkhau123'); await pw('Mật khẩu mới').fill('matkhau456'); await pw('Nhập lại mật khẩu mới').fill('matkhau456');
  await pc.locator('form button[type=submit]').first().click(); await pc.waitForTimeout(900);
  check('doi mat khau -> thong bao may khac da bi dang xuat', (await pc.locator('.el-message').last().innerText().catch(() => '')).includes('đã bị đăng xuất'));
  check('doi mat khau -> dien thoai bi dang xuat', await kickedOut(dt));
  await pc.goto(UI + '/sale'); await pc.waitForTimeout(900);
  check('doi mat khau -> may dang doi van dang nhap', pc.path() === '/sale');

  // ===== 4. Quan tri dat lai mat khau -> moi may cua NV ra =====
  await login(dt, NV, 'matkhau456', { remember: true });
  const nvId = (await api('GET', '/users', null, admin)).find((u) => u.username === NV).id;
  await api('POST', `/users/${nvId}/reset-password`, { password: 'matkhau789' }, admin);
  check('quan tri dat lai mat khau -> dien thoai va PC cua NV deu ra', await kickedOut(dt) && await kickedOut(pc));

  // ===== 5. "Khong hoi ma 2 lop tren may nay" =====
  const qt = (await api('POST', '/auth/login', { username: QT, password: 'matkhau123' })).token;
  const { secret } = await api('POST', '/auth/2fa/setup', { password: 'matkhau123' }, qt);
  const { recoveryCodes } = await api('POST', '/auth/2fa/enable', { code: totp(secret) }, qt);
  const may = await device(), mayKhac = await device();
  check('may moi: hoi ma 6 so', (await login(may, QT, 'matkhau123', { remember: true })) === 'otp');
  check('nhap ma + tich "Khong hoi ma 7 ngay" -> vao', (await login(may, QT, 'matkhau123', { remember: true, otp: totp(secret, step() + 1), trust: true })) === '/dashboard');
  await logout(may);
  check('lan sau tren may do: KHONG hoi ma, vao thang', (await login(may, QT, 'matkhau123', { remember: true })) === '/dashboard');
  check('may khac van bi hoi ma', (await login(mayKhac, QT, 'matkhau123')) === 'otp');
  check('may khac khong tich -> vao bang ma du phong, khong duoc tin cay', (await login(mayKhac, QT, 'matkhau123', { otp: recoveryCodes[0], trust: false })) === '/dashboard'
    && !(await mayKhac.evaluate(() => Object.keys(localStorage).some((k) => k.startsWith('login.trust.')))));
  await logout(mayKhac);
  check('... lan sau may khac van bi hoi ma', (await login(mayKhac, QT, 'matkhau123')) === 'otp');
  // dang xuat moi thiet bi khac -> may tin cay cung mat tin cay
  const qtNow = (await api('POST', '/auth/login', { username: QT, password: 'matkhau123', otp: recoveryCodes[1] })).token;
  await api('POST', '/auth/logout-others', {}, qtNow);
  check('dang xuat moi thiet bi khac -> may tin cay bi dang xuat', await kickedOut(may));
  check('... va phai nhap ma lai', (await login(may, QT, 'matkhau123', { remember: true })) === 'otp');

  check('khong co loi JS', errors.length === 0, errors.join(' | '));
  await browser.close();
  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} PASS`);
  process.exit(failed.length ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(2); });
