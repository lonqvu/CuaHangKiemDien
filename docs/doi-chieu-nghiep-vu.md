# Đối chiếu nghiệp vụ: app Swing cũ (NhomSatKiemDien) ↔ bản Vue 3 + Spring Boot

Mục tiêu: cùng dữ liệu đầu vào thì ra cùng kết quả (số tiền, công nợ, bản in, số liệu thống kê).
Cột "Chỗ trong code" chỉ file của bản mới.

## 1. Giống app cũ (đã kiểm tra bằng test)

| Chức năng | Quy tắc của app cũ | Chỗ trong code |
|---|---|---|
| Tổng SL dòng hàng | `SL × Số tấm × Hệ số`; số tấm và hệ số cùng = 0 thì `= SL`; số tấm = 0 mà hệ số ≠ 0 thì ra 0 | `LineCalculator.totalQuantity` |
| Hệ số tự điền | Thêm hàng: gõ số tấm ≠ 0 → hệ số 1.08. Sửa dòng: gõ số tấm ≠ 0 → hệ số 1 | `SaleView.vue` (watch) + `normalizeFactor` |
| Làm tròn | Tổng SL lưu 3 số lẻ (`#.###`, HALF_EVEN). Thành tiền = đơn giá × Tổng SL **chưa làm tròn**, làm tròn về đồng (`###,###,###`, HALF_EVEN) | `storedQuantity`, `lineTotal` |
| Tiền hàng | Tổng thành tiền các dòng | `OrderService.recalc/checkout` |
| Tổng thanh toán | Tiền hàng + Nợ cũ + Phí vận chuyển | `checkout` |
| Còn lại | `max(0, Tổng thanh toán − Khách trả)`; trả dư thì còn lại 0, lưu nguyên số khách đưa | `checkout` |
| Nợ khách sau đơn | = Còn lại | `checkout` + `debt_ledger` |
| Khách lẻ | Bắt buộc có tên; được trả thiếu, phần còn lại chỉ ghi trên hóa đơn, không vào công nợ | `checkout` |
| Cập nhật nợ cũ (PayDialog) / sửa ô Nợ (Customers) | Đặt lại số nợ của khách | `POST /customers/{id}/debt-adjustments` (ADMIN) |
| Nợ ban đầu khi thêm khách | Nhập được khi tạo khách | `POST /customers` `initialDebt` (ADMIN) |
| Trùng tên khách | Không cho thêm khách trùng tên | `CustomerController.create` |
| Thu nợ (ThanhToanNo) | Số tiền trả ≤ số nợ | `DebtService.pay` |
| Biên lai công nợ (ThanhToanNoReport.jrxml, cùng bố cục) | Các lần trả trong chu kỳ hiện tại (sau lần nợ về 0 / lần sửa nợ tay); dòng trả lúc chốt đơn có "Nợ cũ" = nợ trước hóa đơn | `GET /customers/{id}/payment-receipt`, `DebtReceiptPrint.vue` |
| In hóa đơn (Bill.jrxml) | Dựng lại nguyên bố cục Jasper: cùng tọa độ (pt), cỡ chữ, đường kẻ, font DejaVu Sans, logo KD, QR chuyển khoản, số dạng `1,234,567`, ngày `dd/MM/yyyy HH:mm:ss`. Gộp dòng tôn cùng SP + số tấm + hệ số + giá + ĐVT → `(4.5 + 4.6) x 2 tấm`; nhóm theo tên SP (giảm dần); ô nào Jasper tính cuối nhóm / đầu nhóm thì giữ đúng như vậy | `InvoicePrint.vue`, `jasper.ts`, `public/invoice/`, `public/fonts/` |
| Sản phẩm | Bắt buộc tên, đơn vị, giá | `ProductController` |
| Doanh thu | Tổng thành tiền dòng hàng (không gồm ship, nợ cũ) | `ReportController` |
| Ngày / Tháng / Năm (+ kỳ trước) | Theo lịch | `/reports/overview` |
| Tuần | 7×24h tính từ bây giờ; tuần trước = từ −14 đến −7 ngày | `/reports/overview` |
| Giá trị đơn TB | `AVG(Tổng thanh toán)` (gồm nợ cũ) | `/reports/overview` |
| Tỷ lệ khách quay lại | Khách có ≥ 2 hóa đơn ÷ tổng số khách (khoảng ngày: chỉ đếm khách có đơn trong khoảng) | `customerStat` |
| Top 10 sản phẩm | Sắp theo số lượng hoặc doanh thu | `/reports/top-products?sort=` |
| Top 10 khách | Sắp theo số đơn hoặc tổng "Tổng thanh toán" | `/reports/top-customers?sort=` |
| Theo loại hàng | Số mặt hàng, SL bán, doanh thu | `/reports/categories` |
| Doanh thu 12 tháng | Tháng chưa có = 0 | `/reports/revenue-by-month` |
| Lịch sử hóa đơn | Tất cả hóa đơn đã thanh toán, mới nhất trước, đủ cột (tiền hàng, nợ cũ, tổng, trả, còn lại, khách, SĐT, địa chỉ, ghi chú) | `OrdersView.vue` |
| Đổi mật khẩu | Làm được từ màn đăng nhập (nhập tên đăng nhập + mật khẩu cũ) | `/change-password`, `UserController` |
| Phân quyền | Role 1 → toàn bộ; còn lại → chỉ Bán hàng | `SecurityConfig`, router |

## 2. Cố ý khác (sửa lỗi của app cũ)

| App cũ | Bản mới | Lý do |
|---|---|---|
| Thống kê cộng cả dòng hàng của hóa đơn nháp chưa thanh toán | Chỉ tính hóa đơn đã chốt | Hóa đơn bỏ dở làm doanh thu ảo |
| "Tháng này", "khách mới tháng này" chỉ lọc `MONTH()` | Lọc cả tháng và năm | App cũ cộng cả tháng 11 của mọi năm |
| Xóa hóa đơn = xóa cứng, không trả lại nợ | Hủy hóa đơn: giữ lại (Đã hủy) và hoàn công nợ | Xóa cũ làm lệch nợ khách |
| Xóa khách = xóa luôn mọi hóa đơn của khách | Ẩn khách, giữ lịch sử | Xóa cũ làm sai doanh thu quá khứ |
| Xóa tài khoản | Khóa tài khoản | Giữ tên người lập trên hóa đơn cũ |
| Mật khẩu lưu nguyên văn, xác nhận admin bằng `CheckAdminDialog` | BCrypt + phân quyền theo vai trò | Bảo mật |
| Chốt đơn: hóa đơn, nợ, lịch sử là 3 lệnh rời | 1 transaction | Lỗi giữa chừng không làm lệch dữ liệu |
| Chốt đơn làm nợ về 0 chỉ ẩn dòng lịch sử vừa ghi | Kết thúc cả chu kỳ biên lai (giống màn Thu nợ) | App cũ in lại các lần trả của chu kỳ trước |
| Số lượng nhập 0 vẫn thêm được | Số lượng phải > 0 | Dòng 0 đồng vô nghĩa |
| Sửa nợ không cần quyền | Chỉ quản trị | Tránh nhân viên sửa nợ |

## 3. Chưa làm

- Gửi Zalo, lưu PDF vào `D:/HoaDon`: dùng "In → Lưu thành PDF" của trình duyệt.
