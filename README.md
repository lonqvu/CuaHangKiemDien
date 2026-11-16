# Cua Hang Kiem Dien

Quan ly ban hang + cong no khach hang (chuyen tu app Java Swing "NhomSatKiemDien" sang Vue 3 + Spring Boot).

## Cau truc
```
backend/    Spring Boot 3 (Java 21), JPA, Flyway, JWT
frontend/   Vue 3 + Vite + TypeScript + Pinia + Element Plus
```

## Chay local
```bash
# 1. DB
docker run -d --name shop-db -e POSTGRES_DB=shop -e POSTGRES_USER=shop -e POSTGRES_PASSWORD=shop -p 5432:5432 postgres:16
# 2. Backend (http://localhost:8080) - Flyway tu tao bang, tu tao tai khoan admin / admin123
cd backend && mvn spring-boot:run
# 3. Frontend (http://localhost:5173)
cd frontend && npm install && npm run dev
```
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
- Khach le (khong chon khach) phai tra du tien, khong ghi no.
- Bao cao: doanh thu theo ngay (tach **doanh thu** va **tien thuc thu**), top 10 san pham. Chi tinh hoa don da chot.

## Test
- Backend: `cd backend && mvn test` (gom ca kich ban ban hang, mo phong ngau nhien 300 thao tac kiem tra cong no, 419 case tinh tien).
- Frontend: `cd frontend && npm test` (cung 419 case tinh tien voi backend + doc so `4,2` / `95.000`).
- Bo case tinh tien dung chung: `backend/src/test/resources/line-cases.json`, sinh bang `python3 scripts/gen-line-cases.py` (ket qua tinh doc lap bang Decimal).
- E2E man Ban hang tren backend that: `scripts/e2e-ban-hang.cjs` (xem huong dan dau file).

## In hoa don / bien lai (JasperReports cu)
- Mau in: `backend/src/main/resources/reports/Bill.jrxml` (lay nguyen tu app cu), `PhieuThuNo.jrxml` (phieu thu / sao ke, dau trang lay tu mau bien lai cu), anh logo/QR o `reports/images/`.
- Server nap ban da bien dich san `*.jasper` (JasperReports 5.5.0, giong app cu) -> `GET /api/orders/{id}/invoice.pdf`, `GET /api/customers/debt-payments/{ledgerId}/receipt.pdf` (phieu thu), `GET /api/customers/{id}/statement.pdf?from=&to=` (sao ke).
- Thu vien PDF: `com.lowagie:itext:2.1.7` tu Maven Central (ban `2.1.7.js2` Jasper keo theo chi co tren kho Jaspersoft, CI/Docker khong tai duoc).
- O chu trong mau in phai dat font `DejaVu Sans`; de font mac dinh thi chu co dau (ư, ề, ạ...) bi mat khi xuat PDF.
- Sua `.jrxml` xong phai bien dich lai: `cd backend && mvn test -Dtest=ReportTemplatesTest -Dreports.compile=true`.
- Dinh dang so: bien `REPORT_LOCALE` (mac dinh `en-US` -> 1,234,567; `vi-VN` -> 1.234.567). Gio: `APP_TZ` (mac dinh Asia/Ho_Chi_Minh).

## Chua lam (TODO)
- Gui hoa don qua Zalo.
- Script chuyen du lieu tu DB SQL Server cu (`script.sql` repo cu) sang schema moi.
- Don dep hoa don nhap bi bo quen (job dinh ky).
- Ton kho / nhap hang (hoi chu cua hang co can khong).

## API chinh
| | |
|---|---|
| `POST /api/auth/login` | dang nhap |
| `GET/POST/PUT/DELETE /api/products`, `/api/categories` | san pham (ghi: ADMIN) |
| `GET/POST/PUT/DELETE /api/customers`, `GET /{id}/debt-ledger`, `POST /{id}/debt-payments` | khach hang, so no, thu no |
| `POST /api/orders`, `POST /{id}/items`, `PUT/DELETE /{id}/items/{itemId}`, `POST /{id}/checkout`, `POST /{id}/cancel` | hoa don |
| `GET /api/reports/overview`, `/revenue?from&to`, `/top-products` | bao cao (ADMIN) |
| `GET/POST/PUT /api/users`, `POST /{id}/reset-password` | tai khoan (ADMIN) |
| `POST /api/auth/change-password` | doi mat khau (moi nguoi) |
