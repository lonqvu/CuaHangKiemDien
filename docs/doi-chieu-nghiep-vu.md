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
| Khách lẻ | Bắt buộc có tên | `checkout` |
| Cập nhật nợ cũ (PayDialog) / sửa ô Nợ (Customers) | Đặt lại số nợ của khách | `POST /customers/{id}/debt-adjustments` (ADMIN, **bắt buộc lý do**) |
| Nợ ban đầu khi thêm khách | Nhập được khi tạo khách | `POST /customers` `initialDebt` (ADMIN) |
| Trùng tên khách | Không cho thêm khách trùng tên | `CustomerController.create` |
| Thu nợ (ThanhToanNo) | Số tiền trả ≤ số nợ | `DebtService.pay` |
| In hóa đơn (Bill.jrxml) | **Dùng chính JasperReports 5.5.0 và file `Bill.jrxml` của app cũ** (chỉ đổi đường dẫn ảnh `D:\...` thành tham số). Dữ liệu dựng giống câu SQL trong file: gộp dòng tôn → `(4.5 + 4.6) x 2 tấm`, sắp theo tên SP giảm dần, hệ số, số tấm. Locale en-US (`1,234,567`) | `report/jasper/*`, `resources/reports/` |
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
| Khách lẻ được trả thiếu, phần còn lại chỉ ghi trên hóa đơn | Khách lẻ phải trả đủ tiền hàng + ship; muốn ghi nợ thì chọn / tạo khách (chặn ở cả màn hình và server) | Khoản thiếu của khách lẻ không có sổ nào theo dõi để thu |
| Thống kê theo loại hàng: bỏ sót hàng chưa phân loại, "số mặt hàng" đếm cả hàng đã xóa | Thêm dòng "Chưa phân loại"; chỉ đếm mặt hàng đang bán | Tổng các loại = tổng doanh thu, tỷ trọng đủ 100% |
| Xóa khách (kể cả đang nợ) | Không ẩn được khách còn nợ (thu hết / điều chỉnh về 0 có lý do trước) | Nợ không biến khỏi "Khách đang nợ" |
| Nhân viên vào màn Bán hàng (`HomeUser`) | Server chặn nhân viên đọc sổ nợ, sao kê, danh sách hóa đơn, báo cáo (403); khóa tài khoản có hiệu lực ngay cả với phiên đang đăng nhập | Phân quyền ở server, không chỉ ẩn menu |

## 3. Biên lai công nợ: thay hẳn cách làm của app cũ

App cũ (`ThanhToanNo` + `ThanhToanNoReport.jrxml`, bảng `PaymentHistory`) chỉ liệt kê các lần **trả**, theo "chu kỳ" từ lần nợ về 0 hoặc lần sửa nợ tay:
không thấy khoản mua thêm nên dòng "Nợ cũ − Thanh toán ≠ Còn nợ"; trả hết nợ thì biên lai trống ("Không có bản ghi thanh toán nào");
sửa SĐT khách cũng xóa lịch sử biên lai; in lại thì ra nội dung khác. Bản mới in **từ sổ công nợ** (`debt_ledger`):

| Quy tắc | Chỗ trong code |
|---|---|
| Mỗi lần thu nợ có số phiếu `PT000001…`, hình thức (tiền mặt / chuyển khoản), người thu | `DebtService.pay`, migration `V3__debt_receipts.sql` |
| Phiếu thu 5 cột Ngày / Nội dung / Tăng nợ / Giảm nợ / Còn nợ; mỗi dòng Còn nợ = dòng trên + Tăng − Giảm. Gồm các phát sinh từ sau lần gần nhất nợ = 0 **trước** lần thu đến hết lần thu (trả hết nợ vẫn có phiếu); > 15 dòng thì gộp "Số dư mang sang" | `DebtStatementService.receipt`, `PhieuThuNo.jrxml` |
| In lại luôn ra y nguyên (chỉ dùng các dòng sổ cái tới lần thu đó); phiếu bị hủy có dấu "ĐÃ HỦY" + lý do | `DebtStatementService.receipt` |
| Trả lúc mua hàng: không có phiếu riêng (hóa đơn đã ghi), hiện trên phiếu / sao kê là "Trả khi mua HĐ …" với số tiền **thực trừ vào nợ** (không gồm tiền thối) | `DebtStatementService.describe` |
| Hủy phiếu thu nhập nhầm: thêm dòng đảo ngược (bắt buộc lý do), không xóa; mỗi phiếu hủy 1 lần | `DebtService.voidPayment` |
| Điều chỉnh nợ: bắt buộc lý do, hiện thành dòng "Điều chỉnh: lý do", không cắt lịch sử | `DebtService.setDebt`, `DebtAdjustDialog.vue` |
| Sao kê theo khoảng ngày: số dư đầu kỳ + từng phát sinh + tổng tăng / giảm + số dư cuối kỳ | `DebtStatementService.statement` |
| Chốt đơn gửi kèm nợ cũ đang hiện; máy khác vừa đổi nợ thì chặn (409) và tải lại, để nợ trên hóa đơn / phiếu khớp với số đã đọc cho khách | `OrderService.checkout` (`expectedPreviousDebt`) |

Kiểm thử: `DebtReceiptTest` (cộng trừ khớp từng dòng, in lại giống hệt, ngẫu nhiên 250 thao tác), E2E `scripts/e2e-ban-hang.cjs` mục 10–11.

Lỗi ô **Ghi chú** mất dấu trên hóa đơn in (`Bill.jrxml` không khai báo font) đã sửa: đặt `DejaVu Sans` như các ô khác.

## 4. Chưa làm

- Gửi Zalo, lưu PDF vào `D:/HoaDon`: dùng "In → Lưu thành PDF" của trình duyệt.
