# Cua Hang Kiem Dien

Quan ly ban hang + cong no khach hang (chuyen tu app Java Swing "NhomSatKiemDien" sang Vue 3 + Spring Boot).

## Cau truc
```
backend/    Spring Boot 3 (Java 21), JPA, Flyway, JWT
frontend/   Vue 3 + Vite + TypeScript + Pinia + Element Plus
```

## Chay local
```bash
# 1. DB (cong 5433 de khong dung Postgres khac dang chiem 5432)
docker run -d --name shop-db -e POSTGRES_DB=shop -e POSTGRES_USER=shop -e POSTGRES_PASSWORD=shop -p 5433:5432 postgres:16
# 2. Backend (http://localhost:8090) - Flyway tu tao bang, tu tao tai khoan admin / admin123
cd backend && ./mvnw spring-boot:run      # khong can cai Maven; PowerShell/cmd: .\mvnw.cmd spring-boot:run
# 3. Frontend (http://localhost:5173)
cd frontend && npm install && npm run dev
```
Du lieu mau (5 loai hang, 30 san pham, 16 khach, ~960 hoa don rai 14 thang, cong no + so cai khop nhau, them tai khoan `nhanvien` / `nhanvien123`):
chay backend 1 lan voi `SEED_DEMO=true ./mvnw spring-boot:run`. Chi chay khi DB chua co san pham va hoa don da chot; khong bat tren server that.
Muon tao lai tu dau: `docker exec shop-db psql -U shop -d shop -c "TRUNCATE debt_ledger, order_item, orders, product, category, customer RESTART IDENTITY CASCADE; ALTER SEQUENCE order_code_seq RESTART WITH 1;"`

Hoac chay tat ca: `cp .env.example .env` (doi gia tri) roi `docker compose up --build` -> http://localhost:8081

> Doi mat khau admin mac dinh (`ADMIN_PASSWORD`) va `JWT_SECRET` truoc khi dua len server that.

## Man hinh (doi chieu app Swing cu)
Thiet ke theo skill `ui-ux-pro-max`, design system o `design-system/nhom-sat-kiem-dien/MASTER.md`.

| App cu (Swing) | Man moi | Ai dung |
|---|---|---|
| `Login` | Dang nhap | tat ca |
| `ChangePassWord` | Doi mat khau | tat ca |
| `Home`, phan so lieu cua `ThongKe` | Tong quan (`/dashboard`) | ADMIN |
| `HomeUser` | Nhan vien vao thang man Ban hang | STAFF |
| `Sale` + `ProductDialog` + `ModifyProduct` + `CustomerDialog` + `PayDialog` | Ban hang (`/sale`), in hoa don bang trinh duyet | tat ca |
| `OrderHistory` + `OrderDetailDialog` | Hoa don (`/orders`) | ADMIN |
| `Customers` + `ThanhToanNo` | Khach hang & cong no (`/customers`): thu no, phieu thu, sao ke, dieu chinh no | ADMIN |
| `Product` | San pham (`/products`) | ADMIN |
| `Data` | Loai hang (`/categories`) | ADMIN |
| `ThongKe` | Thong ke (`/reports`) | ADMIN |
| `Account` | Tai khoan (`/accounts`) | ADMIN |
| `CheckAdminDialog` | Bo: thay bang phan quyen theo vai tro | |
| `DatabaseBackup`, `EmployeesManagement`, `Find`, `Note`, `Revenue` | Khong lam: code cu khong man nao goi toi | |

Doi chieu tung cong thuc voi app cu: [`docs/doi-chieu-nghiep-vu.md`](docs/doi-chieu-nghiep-vu.md).

## Nghiep vu da lam (va khac app cu)
- Dang nhap JWT, 2 vai tro: `ADMIN` (toan quyen), `STAFF` (chi man Ban hang). Mat khau BCrypt.
- San pham / loai hang / khach hang: **xoa mem** (khong mat lich su).
- Ban hang: tao hoa don nhap -> them dong hang -> **chot hoa don**.
  Cong thuc dong hang giu nguyen app cu: `Tong SL = SL x So tam x He so` (he so mac dinh 1.08 khi co so tam), tinh o backend (`LineCalculator`).
- **Cong no**: moi thay doi (mua chiu, tra no, huy don) co 1 dong trong `debt_ledger` (so cai) de truy vet.
  Chot hoa don = 1 transaction (hoa don + cong no + so cai), loi la rollback het.
  Khach hang co khoa lac quan (`@Version`) chong 2 may ghi de cong no.
- **Phieu thu no / sao ke**: in tu so cong no (moi dong Con no = dong tren + Tang - Giam), so phieu PT..., in lai ra y nguyen; huy phieu thu va dieu chinh no bat buoc ly do. Xem `docs/doi-chieu-nghiep-vu.md` muc 3.
- **Huy hoa don** (chi ADMIN): giu hoa don o trang thai `CANCELLED` va hoan lai cong no (app cu xoa cung va lam lech no).
  Neu khach da tra bot/tra het no cua chinh hoa don do: no chi ve 0 (khong am), phan con lai cua hang **tra lai khach tien mat**
  (bao truoc khi huy, luu o `refund_amount` cua hoa don va ghi trong so cong no).
- Khach le (khong chon khach): bat buoc co ten va **phai tra du** (tien hang + ship), khong ghi no; muon ghi no thi chon / tao khach.
- **Ma hoa don (HDxxxxxxxx) chi cap khi chot**: hoa don nhap chua co ma, so hoa don lien tuc. Nhap bo do hien o tab "Nhap bo do" man Hoa don (mo ban tiep / xoa).
- Bao cao: doanh thu theo ngay (tach **doanh thu** va **tien thuc thu**), top 10 san pham. Chi tinh hoa don da chot.

## Kho hang (nhap kho, ton kho, kiem ke, nha cung cap)
Mo hinh giong cong no: **so kho bat bien** (`stock_movement`, chi them, huy = ghi nguoc) + **so du** (`stock_balance`), cap nhat cung transaction.
Bat bien: `on_hand = SUM(stock_movement.qty)` (co test).
- **Don vi ton = don vi ban** cua san pham (m, cay, kg...). Ban hang tru **Tong SL** (SL x tam x he so).
  Neu he so 1,08 chi de tinh tien (khong phai hang xuat that): dat `STOCK_SALE_QTY=without-factor` -> tru SL x so tam.
- **Don vi quy doi khi nhap** (man San pham): 1 kg = 0,318 m, 1 bo = 10 cay... Phieu nhap tu quy ra don vi goc, **sua duoc theo so do thuc te**;
  so da quy doi luu tren phieu nen sua ty le sau nay khong lam sai so cu.
- **Gia von binh quan di dong** (nhu KiotViet): tinh lai moi lan nhap; xuat theo binh quan hien tai. Ton dang <= 0 ma nhap -> binh quan = gia nhap.
  Dong hoa don luu `cost_total` luc chot -> bao cao **lai gop** (`/reports/profit`). Ban khi chua tung co gia von: van xuat kho, gia von de trong.
- **Cho ban am kho, canh bao**: man Ban hang bao "Vuot ton kho"; man Ton kho co loc "Am kho". Khong chan ban o quay.
- **Phieu nhap**: nhap -> ghi so (cap ma PNxxxxxx, cong kho, ghi no NCC phan chua tra) -> huy (tru lai dung so da nhap, hoan no NCC).
- **Kiem ke**: lap phieu -> dem (tu luu) -> ghi so chenh lech so voi ton **luc ghi so**. Phieu dau tien = **ton dau ky** (nhap them gia von).
- **Dieu chinh / xuat huy** (hao hut khi cat, hu hong...) o the kho, bat buoc ly do. **Muc ton toi thieu** -> loc "Sap het".
- **Nha cung cap + cong no NCC**: cung mau khach hang (so no, tra tien, sua no).
- Chong ban trung: khoa `stock_balance` (`SELECT ... FOR UPDATE`) theo thu tu product_id tang dan (khong deadlock). Co test 8 don chot song song.
- **Bat dau dung that**: hoa don cu (truoc khi co kho) khong tru kho. Lap 1 phieu kiem ke "Ton dau ky" dem toan bo hang + gia von, roi moi ban.
- Chua lam: theo doi tung cuon ton (ma cuon), nhieu kho / chuyen kho, phan bo chi phi van chuyen vao gia von, ma vach.

## Danh sach & giao dien
- Moi danh sach (hoa don, khach, san pham, so no) **phan trang o server**: `?page` (tu 0) `&size` (20/50/100, toi da 100) `&sort=truong,asc|desc`,
  tra ve `{ items, total, page, size, summary }`. `summary` tinh tren **toan bo ket qua loc** (tong tien hang, da thu, tong no...).
- Frontend: `composables/usePagedList.ts` giu trang/loc/sap xep tren URL (Quay lai, tai lai, gui link deu giu dung danh sach);
  `/orders/123`, `/customers/45` mo thang chi tiet. `components/Pager.vue` dung chung.
- Loi server luon tra `{ "message": "..." }` tieng Viet; `/error` duoc mo trong SecurityConfig de loi 500 khong bi doi thanh 401 (truoc day lam dang xuat nguoi dung).
- Man Ban hang: F2 chon san pham, F4 chon khach, F9 toi nut Chot (Enter de chot). Tim san pham / khach **khong dau, theo tu**, xep hang (Enter chon ket qua dau).
- O nhap so dung `components/NumberField.vue` (khong dung `el-input-number`: no hieu "4,2" thanh 42).

## Test
- Backend: `cd backend && ./mvnw test` (gom ca kich ban ban hang, mo phong ngau nhien 300 thao tac kiem tra cong no, 419 case tinh tien).
- Frontend: `cd frontend && npm test` (cung 419 case tinh tien voi backend + doc so `4,2` / `95.000`).
- Bo case tinh tien dung chung: `backend/src/test/resources/line-cases.json`, sinh bang `python3 scripts/gen-line-cases.py` (ket qua tinh doc lap bang Decimal).
- E2E man Ban hang tren backend that: `scripts/e2e-ban-hang.cjs` (xem huong dan dau file).
- IT toan he thong (dang nhap/phan quyen, tai khoan, danh muc, khach, hoa don + ban in, loc hoa don, bao cao doi chieu doc lap, mo tung trang voi 2 vai tro): `scripts/it-toan-he-thong.cjs`. Chay moi script tren mot DB trong rieng.
- Mo phong thuc te (du lieu ngau nhien co seed, chay qua giao dien, do so phim / click / thoi gian, doi chieu so lieu, bat bien so no + so kho): `SEED=7 SALES=40 node scripts/mo-phong-thuc-te.cjs` tren backend `SEED_DEMO=true` (DB trong). Cho du lieu mau tao xong (log `SEED_DEMO: da tao`) roi moi chay.

## In hoa don / phieu thu (JasperReports cu)
- Mau in: `backend/src/main/resources/reports/Bill.jrxml` (lay nguyen tu app cu), `PhieuThuNo.jrxml` (phieu thu / sao ke, dau trang lay tu mau bien lai cu), anh logo/QR o `reports/images/`.
- Server nap ban da bien dich san `*.jasper` (JasperReports 5.5.0, giong app cu) -> `GET /api/orders/{id}/invoice.pdf|png`, `GET /api/customers/debt-payments/{ledgerId}/receipt.pdf|png` (phieu thu), `GET /api/customers/{id}/statement.pdf|png?from=&to=` (sao ke). Ban `.png` de gui Zalo.
- Thu vien PDF: `com.lowagie:itext:2.1.7` tu Maven Central (ban `2.1.7.js2` Jasper keo theo chi co tren kho Jaspersoft); da in thu hoa don + phieu thu day du tieng Viet.
- O chu trong mau in phai dat font `DejaVu Sans`; de font mac dinh thi chu co dau (ư, ề, ạ...) bi mat khi xuat PDF.
- Sua `.jrxml` xong phai bien dich lai: `cd backend && ./mvnw test -Dtest=ReportTemplatesTest -Dreports.compile=true`.
- Dinh dang so: bien `REPORT_LOCALE` (mac dinh `en-US` -> 1,234,567; `vi-VN` -> 1.234.567). Gio: `APP_TZ` (mac dinh Asia/Ho_Chi_Minh).

## Chua lam (TODO)
- Gui hoa don qua Zalo.
- Script chuyen du lieu tu DB SQL Server cu (`script.sql` repo cu) sang schema moi.
- Don dep hoa don nhap bi bo quen (job dinh ky). Hien da xoa duoc tay o tab "Nhap bo do".
- Tim kiem khong dau ("phu gia" tim ra "Phú Gia"): can extension `unaccent` cua PostgreSQL hoac cot ten da bo dau.

## API chinh
| | |
|---|---|
| `POST /api/auth/login` | dang nhap |
| `GET /api/products?q&categoryId&active&page&size&sort`, `POST/PUT/DELETE /api/products`, `POST /{id}/restore` | san pham (ghi: ADMIN); `active=false` = ngung ban |
| `GET/POST/PUT/DELETE /api/categories` | loai hang, GET kem `productCount` |
| `GET /api/customers?q&debt&page&size&sort`, `GET /{id}`, `GET /{id}/debt-ledger?page&size`, `POST /{id}/debt-payments` | khach hang, so no (kem `orderCode`), thu no |
| `GET /api/orders?status=PAID,CANCELLED,DRAFT&from&to&q&customerId&page&size&sort` | danh sach hoa don (khong kem dong hang) + `summary.countByStatus` |
| `GET /api/orders/{id}`, `POST /api/orders`, `POST /{id}/items`, `PUT/DELETE /{id}/items/{itemId}`, `POST /{id}/checkout`, `POST /{id}/cancel` | hoa don |
| `GET /api/stock?q&categoryId&filter=all\|low\|negative\|out`, `GET /{productId}`, `GET /{productId}/movements`, `POST /{productId}/adjust`, `PUT /{productId}/min-stock` | ton kho, the kho, dieu chinh (ADMIN) |
| `GET/PUT /api/products/{id}/units` | don vi quy doi khi nhap |
| `GET/POST /api/receipts`, `GET/PUT/DELETE /{id}`, `POST /{id}/post`, `POST /{id}/cancel` | phieu nhap (ADMIN) |
| `GET/POST /api/stock-counts`, `GET /{id}`, `PUT /{id}/lines`, `POST /{id}/post`, `DELETE /{id}` | kiem ke (ADMIN) |
| `GET/POST/PUT/DELETE /api/suppliers`, `GET /{id}/ledger`, `POST /{id}/payments`, `POST /{id}/debt-adjustments` | nha cung cap + cong no NCC (ADMIN) |
| `GET /api/reports/overview`, `/revenue?from&to`, `/top-products`, `/profit?from&to` | bao cao, lai gop (ADMIN) |
| `GET/POST/PUT /api/users`, `POST /{id}/reset-password` | tai khoan (ADMIN) |
| `POST /api/auth/change-password` | doi mat khau (moi nguoi) |
