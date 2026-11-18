/*
 * IT toan he thong tren backend THAT (DB trong) + frontend that: dang nhap / phan quyen, tai khoan, doi mat khau,
 * danh muc, khach hang, hoa don + ban in, loc hoa don, BAO CAO (doi chieu voi phep tinh doc lap tu du lieu goc),
 * va mo tung trang voi 2 vai tro. Bo sung cho scripts/e2e-ban-hang.cjs (luong ban hang / cong no).
 *
 * Chay giong e2e-ban-hang.cjs (backend H2 trong tren :8090, frontend dev :5173), roi:
 *   node scripts/it-toan-he-thong.cjs        # PASS/FAIL tung muc, exit 1 neu co FAIL
 * Script tao du lieu moi moi lan chay -> chi chay tren DB trong, KHONG chay tren DB that.
 */
const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const API = (process.env.API_URL ?? 'http://localhost:8090') + '/api';
const UI = 'http://localhost:5173';
const results = [];
function check(name, ok, detail = '') { results.push({ name, ok: !!ok, detail }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`); }
async function call(token, method, p, body) {
  const r = await fetch(API + p, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  let data = null; try { data = t ? JSON.parse(t) : null; } catch { data = t; }
  return { status: r.status, data };
}
async function ok(token, method, p, body) {
  const r = await call(token, method, p, body);
  if (r.status >= 300) throw new Error(`${method} ${p} -> ${r.status} ${JSON.stringify(r.data)}`);
  return r.data;
}
// API danh sach co phan trang ({ items, total }): doc het cac trang
async function allOf(token, p) {
  const out = [];
  for (let page = 0; ; page++) {
    const r = await ok(token, 'GET', `${p}${p.includes('?') ? '&' : '?'}page=${page}&size=100`);
    if (Array.isArray(r)) return r;
    out.push(...r.items);
    if (out.length >= r.total || !r.items.length) return out;
  }
}
async function pdfText(token, p) {
  const r = await fetch(API + p, { headers: { Authorization: 'Bearer ' + token } });
  const f = path.join(os.tmpdir(), 'it-' + Date.now() + Math.random() + '.pdf');
  fs.writeFileSync(f, Buffer.from(await r.arrayBuffer()));
  return { status: r.status, text: r.status === 200 ? execFileSync('pdftotext', ['-layout', f, '-']).toString() : '' };
}
const login = (u, p) => call(null, 'POST', '/auth/login', { username: u, password: p });
const fmt = (n) => Number(n).toLocaleString('en-US'); // so tren ban in (Jasper en-US)
const sum = (a) => a.reduce((x, y) => x + y, 0);
const N = Number;
const today = new Date();
// ngay theo gio cua hang (Viet Nam, giong backend) - may chay test o UTC thi 0h-7h sang VN van ra dung "hom nay"
const TZ = process.env.SHOP_TZ ?? 'Asia/Ho_Chi_Minh';
const iso = (d) => new Intl.DateTimeFormat('en-CA', { timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit' }).format(d);
const TODAY = iso(today), TOMORROW = iso(new Date(today.getTime() + 864e5));

(async () => {
  const admin = (await login('admin', 'admin123')).data.token;

  // ========== A. Dang nhap, tai khoan, phan quyen ==========
  check('dang nhap sai mat khau -> 401', (await login('admin', 'sai-mat-khau')).status === 401);
  check('dang nhap tai khoan khong ton tai -> 401', (await login('khong-co', 'x123456')).status === 401);
  const nv = await ok(admin, 'POST', '/users', { username: 'nv1', fullName: 'Nguyễn Thị Thu', role: 'STAFF', password: 'nv123456' });
  check('tao trung ten dang nhap -> 409', (await call(admin, 'POST', '/users', { username: 'nv1', fullName: 'X', role: 'STAFF', password: 'nv123456' })).status === 409);
  check('mat khau < 6 ky tu -> 400', (await call(admin, 'POST', '/users', { username: 'nv2', fullName: 'X', role: 'STAFF', password: '123' })).status === 400);
  const lg = await login('nv1', 'nv123456');
  check('nhan vien dang nhap duoc, vai tro STAFF, ho ten dung', lg.status === 200 && lg.data.role === 'STAFF' && lg.data.fullName === 'Nguyễn Thị Thu');
  const staff = lg.data.token;

  // du lieu danh muc (admin)
  const cTon = await ok(admin, 'POST', '/categories', { name: 'Tôn' });
  const cSat = await ok(admin, 'POST', '/categories', { name: 'Sắt hộp' });
  const ton = await ok(admin, 'POST', '/products', { name: 'Tôn lạnh 0,4 ly', categoryId: cTon.id, unit: 'm', price: 95000 });
  const hop = await ok(admin, 'POST', '/products', { name: 'Hộp sắt 40x80 dày 1,4', categoryId: cSat.id, unit: 'cây', price: 285000 });
  const vit = await ok(admin, 'POST', '/products', { name: 'Vít bắn tôn', categoryId: null, unit: 'hộp', price: 45000 }); // chua phan loai

  // nhan vien: duoc lam
  check('NV: xem san pham', (await call(staff, 'GET', '/products?q=ton')).status === 200);
  check('NV: tim khach', (await call(staff, 'GET', '/customers?q=')).status === 200);
  const kNV = await call(staff, 'POST', '/customers', { name: 'Khách do NV tạo', phone: '0900000001' });
  check('NV: tao khach (khong no ban dau)', kNV.status === 200);
  check('NV: tao khach CO no ban dau -> 403', (await call(staff, 'POST', '/customers', { name: 'Khách NV nợ', initialDebt: 100000 })).status === 403);
  const oNV = await ok(staff, 'POST', '/orders');
  await ok(staff, 'POST', `/orders/${oNV.id}/items`, { productId: hop.id, quantity: 1, sheets: 0, factor: 0, unitPrice: 285000 });
  const paidNV = await call(staff, 'POST', `/orders/${oNV.id}/checkout`, { customerId: kNV.data.id, shippingFee: 0, paidAmount: 85000, expectedPreviousDebt: 0 });
  check('NV: ban chiu + chot hoa don, nguoi lap = ho ten NV', paidNV.status === 200 && paidNV.data.staffName === 'Nguyễn Thị Thu', paidNV.data?.staffName);
  check('NV: in hoa don cua minh', (await pdfText(staff, `/orders/${oNV.id}/invoice.pdf`)).status === 200);
  // nhan vien: KHONG duoc lam (chi man Ban hang)
  const forbidden = [
    ['GET', '/orders?status=PAID', 'xem danh sach hoa don'], ['GET', '/reports/overview', 'xem bao cao'], ['GET', '/users', 'xem tai khoan'],
    ['POST', '/products', 'them san pham', { name: 'x', unit: 'm', price: 1 }], ['POST', `/customers/${kNV.data.id}/debt-payments`, 'thu no', { amount: 1000 }],
    ['POST', `/customers/${kNV.data.id}/debt-adjustments`, 'sua no', { debt: 0, note: 'x' }], ['GET', `/customers/${kNV.data.id}/debt-ledger`, 'xem so no'],
    ['GET', `/customers/${kNV.data.id}/statement`, 'xem sao ke'], ['GET', `/customers/${kNV.data.id}/statement.pdf`, 'in sao ke'],
    ['POST', `/orders/${oNV.id}/cancel`, 'huy hoa don'], ['GET', `/orders/${oNV.id}/cancel-preview`, 'xem truoc huy'],
    ['DELETE', `/customers/${kNV.data.id}`, 'an khach'], ['PUT', `/customers/${kNV.data.id}`, 'sua khach', { name: 'x' }],
  ];
  for (const [m, p, what, body] of forbidden) {
    const st = (await call(staff, m, p, body)).status;
    check(`NV khong duoc ${what} -> 403`, st === 403, `${m} ${p} -> ${st}`);
  }
  check('chua dang nhap -> 401', (await call(null, 'GET', '/products')).status === 401);
  check('token gia -> 401', (await call('abc.def.ghi', 'GET', '/products')).status === 401);

  // doi mat khau tu man dang nhap
  check('doi mat khau: sai mat khau cu -> 400', (await call(null, 'POST', '/auth/change-password', { username: 'nv1', oldPassword: 'sai', newPassword: 'moi123456' })).status === 400);
  check('doi mat khau: mat khau moi < 6 -> 400', (await call(null, 'POST', '/auth/change-password', { username: 'nv1', oldPassword: 'nv123456', newPassword: '12' })).status === 400);
  check('doi mat khau tu man dang nhap', (await call(null, 'POST', '/auth/change-password', { username: 'nv1', oldPassword: 'nv123456', newPassword: 'moi123456' })).status === 200);
  check('mat khau cu khong dung nua, mat khau moi dung', (await login('nv1', 'nv123456')).status === 401 && (await login('nv1', 'moi123456')).status === 200);
  await ok(admin, 'POST', `/users/${nv.id}/reset-password`, { password: 'reset123' });
  check('quan tri dat lai mat khau', (await login('nv1', 'reset123')).status === 200);
  const staff2 = (await login('nv1', 'reset123')).data.token;
  // bao ve tai khoan quan tri
  const users = await ok(admin, 'GET', '/users');
  const adminUser = users.find((u) => u.username === 'admin');
  check('khong tu khoa / tu ha quyen chinh minh', (await call(admin, 'PUT', `/users/${adminUser.id}`, { fullName: adminUser.fullName, role: 'STAFF', active: true })).status === 400);
  // khoa tai khoan
  await ok(admin, 'PUT', `/users/${nv.id}`, { fullName: 'Nguyễn Thị Thu', role: 'STAFF', active: false });
  check('tai khoan bi khoa: khong dang nhap duoc', (await login('nv1', 'reset123')).status === 401);
  const stLocked = (await call(staff2, 'POST', '/orders')).status;
  check('tai khoan bi khoa: token cu bi chan ngay', stLocked === 401, `POST /orders bang token cu -> ${stLocked}`);
  await ok(admin, 'PUT', `/users/${nv.id}`, { fullName: 'Nguyễn Thị Thu', role: 'STAFF', active: true });

  // ========== B. Danh muc ==========
  check('san pham thieu don vi -> 400', (await call(admin, 'POST', '/products', { name: 'Thiếu ĐV', unit: '', price: 1000 })).status === 400);
  check('san pham gia am -> 400', (await call(admin, 'POST', '/products', { name: 'Giá âm', unit: 'm', price: -1 })).status === 400);
  check('san pham loai hang khong ton tai -> 400', (await call(admin, 'POST', '/products', { name: 'Sai loại', unit: 'm', price: 1, categoryId: 999999 })).status === 400);
  check('tao loai trung ten (khong phan biet hoa thuong) -> 409, khong phai 500', (await call(admin, 'POST', '/categories', { name: 'tôn' })).status === 409);
  check('doi ten loai thanh trung ten loai khac -> 409', (await call(admin, 'PUT', `/categories/${cSat.id}`, { name: 'Tôn' })).status === 409);
  await ok(admin, 'PUT', `/categories/${cSat.id}`, { name: 'Sắt hộp mạ kẽm' });
  check('doi ten loai -> san pham hien ten moi', (await allOf(admin, '/products?q=hop 40x80')).find((p) => p.id === hop.id)?.categoryName === 'Sắt hộp mạ kẽm');
  check('xoa loai con san pham -> 409', (await call(admin, 'DELETE', `/categories/${cTon.id}`)).status === 409);
  const tmp = await ok(admin, 'POST', '/products', { name: 'Mặt hàng ngừng bán', categoryId: cTon.id, unit: 'm', price: 10000 });
  const oTmp = await ok(admin, 'POST', '/orders');
  await ok(admin, 'POST', `/orders/${oTmp.id}/items`, { productId: tmp.id, quantity: 2, sheets: 0, factor: 0 });
  await ok(admin, 'POST', `/orders/${oTmp.id}/checkout`, { customerName: 'Khách lẻ A', shippingFee: 0, paidAmount: 20000 });
  await ok(admin, 'DELETE', `/products/${tmp.id}`);
  check('ngung ban: khong con trong tim kiem', !(await allOf(admin, '/products?q=ngung ban')).some((p) => p.id === tmp.id));
  check('ngung ban: khong them duoc vao hoa don moi', (await call(admin, 'POST', `/orders/${(await ok(admin, 'POST', '/orders')).id}/items`, { productId: tmp.id, quantity: 1, sheets: 0, factor: 0 })).status === 400);
  check('ngung ban: hoa don cu giu ten + gia', (await ok(admin, 'GET', `/orders/${oTmp.id}`)).items[0].productName === 'Mặt hàng ngừng bán');

  // ========== C. Khach hang ==========
  const kA = await ok(admin, 'POST', '/customers', { name: 'Anh Tuấn thợ hàn', phone: '0905123456', address: 'Thôn 3', initialDebt: 2000000 });
  check('trung ten khach (khong phan biet hoa thuong) -> 409', (await call(admin, 'POST', '/customers', { name: 'anh tuấn THỢ HÀN' })).status === 409);
  const kB = await ok(admin, 'POST', '/customers', { name: 'Chị Lan', phone: '0912000111' });
  check('sua ten khach thanh trung ten khach khac -> 409', (await call(admin, 'PUT', `/customers/${kB.id}`, { name: 'Anh Tuấn thợ hàn' })).status === 409);
  await ok(admin, 'PUT', `/customers/${kB.id}`, { name: 'Chị Lan', phone: '0912000222', address: 'Yên Mô' });
  check('sua SDT khach', (await ok(admin, 'GET', `/customers/${kB.id}`)).phone === '0912000222');
  check('an khach dang no -> bi chan (no van tinh vao tong)', (await call(admin, 'DELETE', `/customers/${kA.id}`)).status === 409);
  const kC = await ok(admin, 'POST', '/customers', { name: 'Khách sẽ ẩn' });
  await ok(admin, 'DELETE', `/customers/${kC.id}`);
  check('an khach khong no: khong con trong tim kiem', !(await allOf(admin, '/customers?q=se an')).some((c) => c.id === kC.id));

  // ========== D. Hoa don + ban in ==========
  const o1 = await ok(admin, 'POST', '/orders');
  for (const [p, q, s, f, pr] of [[ton, 4.7, 2, 0, 95000], [ton, 4.5, 2, 0, 95000], [ton, 3, 1, 0, 95000], [hop, 6, 0, 0, 280000], [vit, 2, 0, 0, 45000]])
    await ok(admin, 'POST', `/orders/${o1.id}/items`, { productId: p.id, quantity: q, sheets: s, factor: f, unitPrice: pr });
  const p1 = await ok(admin, 'POST', `/orders/${o1.id}/checkout`, { customerId: kA.id, shippingFee: 150000, paidAmount: 3000000, note: 'Giao trước 5h chiều', expectedPreviousDebt: 2000000 });
  // doc lap: 4,7x2x1,08x95000=964440; 4,5x2x1,08=923400; 3x1x1,08=307800; 6x280000=1680000; 2x45000=90000
  const items1 = 964440 + 923400 + 307800 + 1680000 + 90000;
  check('hoa don: tien hang', N(p1.itemsTotal) === items1, `${p1.itemsTotal} vs ${items1}`);
  check('hoa don: tong = hang + ship + no cu', N(p1.totalPayable) === items1 + 150000 + 2000000);
  check('hoa don: no lai = tong - tra', N(p1.debtAfter) === items1 + 150000 + 2000000 - 3000000);
  const bill = await pdfText(admin, `/orders/${o1.id}/invoice.pdf`);
  const lineOf = (label) => bill.text.split('\n').find((l) => l.includes(label)) ?? '';
  check('ban in hoa don: ma, khach, nguoi ban, ghi chu co dau', bill.status === 200 && bill.text.includes(p1.code) && bill.text.includes('Anh Tuấn thợ hàn') && bill.text.includes('Giao trước 5h chiều'));
  // mau cu gop moi dong ton cung san pham thanh 1 dong: tong tam, tong SL, tong tien; ben duoi liet ke tung doan
  const tonLine = lineOf('Tôn lạnh 0,4 ly');
  check('ban in: dong ton gop = 5 tam, 23.112 m, 2,195,640', tonLine.includes('5 tấm x 1.08') && tonLine.includes('23.112 m') && tonLine.includes(fmt(964440 + 923400 + 307800)), tonLine.replace(/\s+/g, ' ').trim());
  check('ban in: liet ke doan "(4.5 + 4.7) x 2 tấm" va "3 x 1 tấm"', bill.text.includes('(4.5 + 4.7) x 2 tấm') && bill.text.includes('3 x 1 tấm'));
  check('ban in: hop sat 6 cay x 280,000 = 1,680,000', /6 cây\s+280,000\s+1,680,000/.test(bill.text));
  check('ban in: vit 2 hop x 45,000 = 90,000', /2 hộp\s+45,000\s+90,000/.test(bill.text));
  for (const [label, v] of [['Phí vận chuyển', 150000], ['Tổng tiền hóa đơn', items1], ['Nợ cũ', 2000000], ['Tổng:', items1 + 2150000], ['Thanh toán', 3000000], ['Nợ lại', N(p1.debtAfter)]])
    check(`ban in: ${label} ${fmt(v)}`, lineOf(label).includes(fmt(v)), lineOf(label).replace(/\s+/g, ' ').trim());

  // khach le tra thieu, tra thua; khach B tra du; mot hoa don se huy
  const mk = async (lines, body) => { const o = await ok(admin, 'POST', '/orders'); for (const [p, q, s, pr] of lines) await ok(admin, 'POST', `/orders/${o.id}/items`, { productId: p.id, quantity: q, sheets: s, factor: 0, unitPrice: pr }); return ok(admin, 'POST', `/orders/${o.id}/checkout`, body); };
  // khach le phai tra du: thieu (ke ca thieu phi ship) -> 400, hoa don van la nhap
  const oLe = await ok(admin, 'POST', '/orders');
  await ok(admin, 'POST', `/orders/${oLe.id}/items`, { productId: hop.id, quantity: 2, sheets: 0, factor: 0, unitPrice: 285000 });
  const thieu = await call(admin, 'POST', `/orders/${oLe.id}/checkout`, { customerName: 'Khách lẻ B', shippingFee: 0, paidAmount: 500000 });
  const thieuShip = await call(admin, 'POST', `/orders/${oLe.id}/checkout`, { customerName: 'Khách lẻ B', shippingFee: 30000, paidAmount: 570000 });
  check('khach le tra thieu / thieu phi ship -> 400, van la nhap', thieu.status === 400 && thieuShip.status === 400 && (await ok(admin, 'GET', `/orders/${oLe.id}`)).status === 'DRAFT', thieu.data?.message);
  const o2 = await ok(admin, 'POST', `/orders/${oLe.id}/checkout`, { customerName: 'Khách lẻ B', shippingFee: 30000, paidAmount: 600000 }); // tra du
  const o3 = await mk([[vit, 1, 0, 45000]], { customerName: 'Khách lẻ C', shippingFee: 0, paidAmount: 100000 });        // tra thua
  const o4 = await mk([[ton, 10, 0, 95000]], { customerId: kB.id, shippingFee: 0, paidAmount: 950000, expectedPreviousDebt: 0 });
  const o5 = await mk([[hop, 1, 0, 285000], [ton, 2, 0, 95000]], { customerId: kB.id, shippingFee: 0, paidAmount: 0, expectedPreviousDebt: 0 });
  const o6 = await mk([[ton, 1, 0, 95000]], { customerId: kA.id, shippingFee: 0, paidAmount: 0, expectedPreviousDebt: N(p1.debtAfter) });
  await ok(admin, 'POST', `/orders/${o6.id}/cancel`);
  check('khach le tra du: con lai 0, khong gan khach', N(o2.debtAfter) === 0 && o2.customerId === null);
  check('khach le tra thua: con lai 0', N(o3.debtAfter) === 0);
  // loc danh sach hoa don
  const paidToday = await allOf(admin, `/orders?status=PAID&from=${TODAY}&to=${TODAY}`);
  check('loc hoa don hom nay: co du hoa don vua tao', [p1, o2, o3, o4, o5].every((o) => paidToday.some((x) => x.id === o.id)));
  check('loc hoa don ngay mai: rong', (await allOf(admin, `/orders?status=PAID&from=${TOMORROW}&to=${TOMORROW}`)).length === 0);
  const cancelled = await allOf(admin, '/orders?status=CANCELLED');
  check('hoa don huy: o tab Da huy, khong o tab Da thanh toan', cancelled.some((x) => x.id === o6.id) && !paidToday.some((x) => x.id === o6.id));
  check('khong huy lai hoa don da huy -> 409', (await call(admin, 'POST', `/orders/${o6.id}/cancel`)).status === 409);

  // ========== E. Bao cao: doi chieu voi phep tinh doc lap tu hoa don da chot ==========
  const paid = await Promise.all((await allOf(admin, '/orders?status=PAID')).map((o) => ok(admin, 'GET', `/orders/${o.id}`)));
  const allItems = paid.flatMap((o) => o.items.map((i) => ({ ...i, order: o })));
  const revenue = sum(paid.map((o) => N(o.itemsTotal)));
  const collected = sum(paid.map((o) => Math.min(N(o.paidAmount), N(o.totalPayable))));
  const rev = await ok(admin, 'GET', `/reports/revenue?from=${TODAY}&to=${TODAY}`);
  check('doanh thu hom nay = tong tien hang hoa don da chot (khong gom huy, ship, no cu)', rev.length === 1 && N(rev[0].revenue) === revenue, `${rev[0]?.revenue} vs ${revenue}`);
  check('so hoa don hom nay', N(rev[0].orders) === paid.length, `${rev[0]?.orders} vs ${paid.length}`);
  check('tien thuc thu = tong min(khach tra, tong phai tra)', N(rev[0].collected) === collected, `${rev[0]?.collected} vs ${collected}`);
  const ov = await ok(admin, 'GET', '/reports/overview');
  check('tong quan: doanh thu ngay / thang / nam', N(ov.day.current) === revenue && N(ov.month.current) === revenue && N(ov.year.current) === revenue && N(ov.week.current) === revenue);
  check('tong quan: hoa don thang nay', ov.ordersThisMonth === paid.length);
  const avg = Math.round(sum(paid.map((o) => N(o.totalPayable))) / paid.length);
  check('tong quan: gia tri don trung binh (tong thanh toan)', N(ov.avgOrderThisMonth) === avg, `${ov.avgOrderThisMonth} vs ${avg}`);
  const custs = await allOf(admin, '/customers?q=');
  const inDebt = custs.filter((c) => N(c.debt) > 0);
  check('tong quan: tong no khach = tong no cac khach dang no', N(ov.totalDebt) === sum(inDebt.map((c) => N(c.debt))), `${ov.totalDebt}`);
  check('tong quan: so khach dang no', ov.customersInDebt === inDebt.length);
  const byMonth = await ok(admin, 'GET', `/reports/revenue-by-month?year=${today.getFullYear()}`);
  check('doanh thu 12 thang: thang nay = doanh thu, thang khac = 0', byMonth.length === 12 && byMonth.every((m) => N(m.revenue) === (m.month === today.getMonth() + 1 ? revenue : 0)));
  // top san pham
  const prodAgg = {};
  for (const i of allItems) { const k = i.productId; prodAgg[k] ??= { name: i.productName, q: 0, r: 0 }; prodAgg[k].q += N(i.totalQuantity); prodAgg[k].r += N(i.lineTotal); }
  const topR = Object.values(prodAgg).sort((a, b) => b.r - a.r);
  const apiTopR = await ok(admin, 'GET', `/reports/top-products?from=${TODAY}&to=${TODAY}&sort=revenue`);
  check('top san pham theo doanh thu', JSON.stringify(apiTopR.map((x) => [x.productName, N(x.revenue)])) === JSON.stringify(topR.slice(0, 10).map((x) => [x.name, x.r])), apiTopR.map((x) => x.productName + ' ' + x.revenue).join(', '));
  const apiTopQ = await ok(admin, 'GET', `/reports/top-products?from=${TODAY}&to=${TODAY}&sort=quantity`);
  const topQ = Object.values(prodAgg).sort((a, b) => b.q - a.q);
  check('top san pham theo so luong', JSON.stringify(apiTopQ.map((x) => [x.productName, +N(x.totalQuantity).toFixed(3)])) === JSON.stringify(topQ.slice(0, 10).map((x) => [x.name, +x.q.toFixed(3)])));
  // top khach
  const cAgg = {};
  for (const o of paid.filter((o) => o.customerId)) { cAgg[o.customerId] ??= { name: o.customerName, n: 0, a: 0 }; cAgg[o.customerId].n++; cAgg[o.customerId].a += N(o.totalPayable); }
  const apiTopC = await ok(admin, 'GET', `/reports/top-customers?from=${TODAY}&to=${TODAY}&sort=amount`);
  const topC = Object.values(cAgg).sort((a, b) => b.a - a.a || b.n - a.n);
  check('top khach theo tong tien (khong gom khach le)', JSON.stringify(apiTopC.map((x) => [x.customerName, x.orders, N(x.totalAmount)])) === JSON.stringify(topC.map((x) => [x.name, x.n, x.a])), apiTopC.map((x) => `${x.customerName} ${x.orders} ${x.totalAmount}`).join(', '));
  // theo loai hang: tong cac loai phai bang tong doanh thu (ke ca hang chua phan loai)
  const cats = await ok(admin, 'GET', `/reports/categories?from=${TODAY}&to=${TODAY}`);
  check('theo loai hang: co dong "Chua phan loai" cho vit (135.000)', N(cats.find((c) => c.category === 'Chưa phân loại')?.revenue) === 135000);
  check('theo loai hang: tong doanh thu cac loai = tong doanh thu', sum(cats.map((c) => N(c.revenue))) === revenue, `${sum(cats.map((c) => N(c.revenue)))} vs ${revenue}: ${cats.map((c) => c.category + ' ' + c.revenue).join(', ')}`);
  const prods = await allOf(admin, '/products?q=');
  const tonCat = cats.find((c) => c.category === 'Tôn');
  check('theo loai hang: so mat hang = mat hang dang ban trong loai', tonCat && tonCat.productCount === prods.filter((p) => p.categoryId === cTon.id).length, `${tonCat?.productCount} vs ${prods.filter((p) => p.categoryId === cTon.id).length}`);
  // khach quay lai
  const cs = await ok(admin, 'GET', `/reports/customers?from=${TODAY}&to=${TODAY}`);
  const returning = Object.values(cAgg).filter((x) => x.n >= 2).length;
  check('khach quay lai = khach co >= 2 hoa don da chot', cs.returningCustomers === returning && cs.totalCustomers === custs.length, `${cs.returningCustomers}/${cs.totalCustomers} vs ${returning}/${custs.length}`);
  check('ty le khach quay lai', cs.returningRate === Math.round(returning * 1000 / custs.length) / 10, `${cs.returningRate}`);

  // ========== F. Mo tung trang, 2 vai tro ==========
  const browser = await chromium.launch();
  for (const [role, user, pass, routes] of [
    ['admin', 'admin', 'admin123', ['/dashboard', '/sale', '/orders', '/customers', '/products', '/categories', '/inventory', '/receipts', '/receipts/new', '/stock-counts', '/suppliers', '/reports', '/accounts', '/password']],
    ['staff', 'nv1', 'reset123', ['/sale', '/password', '/dashboard', '/orders', '/customers', '/reports', '/accounts', '/inventory', '/receipts', '/stock-counts', '/suppliers']],
  ]) {
    const page = await (await browser.newContext({ viewport: { width: 1366, height: 860 } })).newPage();
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    page.on('response', (r) => { if (r.url().includes('/api/') && r.status() >= 400) errors.push(`${r.status()} ${r.url().split('/api')[1]}`); });
    await page.goto(UI + '/login');
    await page.fill('input[autocomplete=username]', user); await page.fill('input[type=password]', pass); await page.keyboard.press('Enter');
    await page.waitForURL(role === 'admin' ? /dashboard/ : /sale/);
    for (const r of routes) {
      await page.goto(UI + r); await page.waitForTimeout(900);
      const url = new URL(page.url()).pathname;
      const h1 = await page.evaluate(() => document.querySelector('h1')?.innerText ?? '');
      const adminOnly = !['/sale', '/password'].includes(r);
      if (role === 'staff' && adminOnly) check(`NV mo ${r} -> bi dua ve Ban hang`, url === '/sale', url);
      else check(`${role} mo ${r}: hien noi dung`, url === r && h1.length > 0, `${url} "${h1}"`);
    }
    const menu = await page.evaluate(() => [...document.querySelectorAll('.sidebar nav .nav-item')].map((a) => a.innerText.trim()));
    if (role === 'staff') check('menu NV chi co Ban hang', JSON.stringify(menu) === JSON.stringify(['Bán hàng']), menu.join(', '));
    else check('menu quan tri du 12 muc (them 4 muc kho)', menu.length === 12, menu.join(', '));
    check(`${role}: khong co loi JS / API 4xx-5xx khi mo cac trang`, errors.length === 0, errors.slice(0, 5).join(' | '));
  }
  await browser.close();

  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} PASS`);
  process.exit(failed.length ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(2); });
