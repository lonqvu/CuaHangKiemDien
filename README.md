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
| `Customers` + `ThanhToanNo` | Khach hang & cong no (`/customers`) | ADMIN |
| `Product` | San pham (`/products`) | ADMIN |
| `Data` | Loai hang (`/categories`) | ADMIN |
| `ThongKe` | Thong ke (`/reports`) | ADMIN |
| `Account` | Tai khoan (`/accounts`) | ADMIN |
| `CheckAdminDialog` | Bo: thay bang phan quyen theo vai tro | |
| `DatabaseBackup`, `EmployeesManagement`, `Find`, `Note`, `Revenue` | Khong lam: code cu khong man nao goi toi | |

## Nghiep vu da lam (va khac app cu)
- Dang nhap JWT, 2 vai tro: `ADMIN` (toan quyen), `STAFF` (chi man Ban hang). Mat khau BCrypt.
- San pham / loai hang / khach hang: **xoa mem** (khong mat lich su).
- Ban hang: tao hoa don nhap -> them dong hang -> **chot hoa don**.
  Cong thuc dong hang giu nguyen app cu: `Tong SL = SL x So tam x He so` (he so mac dinh 1.08 khi co so tam), tinh o backend (`LineCalculator`).
- **Cong no**: moi thay doi (mua chiu, tra no, huy don) co 1 dong trong `debt_ledger` (so cai) de truy vet.
  Chot hoa don = 1 transaction (hoa don + cong no + so cai), loi la rollback het.
  Khach hang co khoa lac quan (`@Version`) chong 2 may ghi de cong no.
- **Huy hoa don** (chi ADMIN): giu hoa don o trang thai `CANCELLED` va hoan lai cong no (app cu xoa cung va lam lech no).
- Khach le (khong chon khach) phai tra du tien, khong ghi no.
- Bao cao: doanh thu theo ngay (tach **doanh thu** va **tien thuc thu**), top 10 san pham. Chi tinh hoa don da chot.

## Chua lam (TODO)
- Gui hoa don qua Zalo (in hoa don da lam bang trinh duyet).
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
