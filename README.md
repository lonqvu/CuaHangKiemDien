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
| `Customers` + `ThanhToanNo` | Khach hang & cong no (`/customers`) | ADMIN |
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
- **Huy hoa don** (chi ADMIN): giu hoa don o trang thai `CANCELLED` va hoan lai cong no (app cu xoa cung va lam lech no).
- Khach le (khong chon khach): bat buoc co ten; tra thieu thi phan con lai chi ghi tren hoa don, khong vao so cong no.
- **Ma hoa don (HDxxxxxxxx) chi cap khi chot**: hoa don nhap chua co ma, so hoa don lien tuc. Nhap bo do hien o tab "Nhap bo do" man Hoa don (mo ban tiep / xoa).
- Bao cao: doanh thu theo ngay (tach **doanh thu** va **tien thuc thu**), top 10 san pham. Chi tinh hoa don da chot.

## Danh sach & giao dien
- Moi danh sach (hoa don, khach, san pham, so no) **phan trang o server**: `?page` (tu 0) `&size` (20/50/100, toi da 100) `&sort=truong,asc|desc`,
  tra ve `{ items, total, page, size, summary }`. `summary` tinh tren **toan bo ket qua loc** (tong tien hang, da thu, tong no...).
- Frontend: `composables/usePagedList.ts` giu trang/loc/sap xep tren URL (Quay lai, tai lai, gui link deu giu dung danh sach);
  `/orders/123`, `/customers/45` mo thang chi tiet. `components/Pager.vue` dung chung.
- Loi server luon tra `{ "message": "..." }` tieng Viet; `/error` duoc mo trong SecurityConfig de loi 500 khong bi doi thanh 401 (truoc day lam dang xuat nguoi dung).
- Man Ban hang: F2 chon san pham, F4 chon khach, F9 chot hoa don.

## In hoa don / bien lai (JasperReports cu)
- Mau in: `backend/src/main/resources/reports/Bill.jrxml`, `ThanhToanNoReport.jrxml` (lay nguyen tu app cu), anh logo/QR o `reports/images/`.
- Server nap ban da bien dich san `*.jasper` (JasperReports 5.5.0, giong app cu) -> `GET /api/orders/{id}/invoice.pdf`, `GET /api/customers/{id}/payment-receipt.pdf`.
- Sua `.jrxml` xong phai bien dich lai: `cd backend && ./mvnw test -Dtest=ReportTemplatesTest -Dreports.compile=true`.
- Dinh dang so: bien `REPORT_LOCALE` (mac dinh `en-US` -> 1,234,567; `vi-VN` -> 1.234.567). Gio: `APP_TZ` (mac dinh Asia/Ho_Chi_Minh).

## Chua lam (TODO)
- Gui hoa don qua Zalo.
- Script chuyen du lieu tu DB SQL Server cu (`script.sql` repo cu) sang schema moi.
- Don dep hoa don nhap bi bo quen (job dinh ky). Hien da xoa duoc tay o tab "Nhap bo do".
- Ton kho / nhap hang (hoi chu cua hang co can khong).
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
| `GET /api/reports/overview`, `/revenue?from&to`, `/top-products` | bao cao (ADMIN) |
| `GET/POST/PUT /api/users`, `POST /{id}/reset-password` | tai khoan (ADMIN) |
| `POST /api/auth/change-password` | doi mat khau (moi nguoi) |
