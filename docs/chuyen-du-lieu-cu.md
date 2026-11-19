# Chuyển dữ liệu từ phần mềm cũ sang hệ thống mới

Phần mềm cũ (Java Swing + SQL Server) tự sao lưu mỗi lần mở vào `D:\Sao lưu dữ liệu\BackupFile.bak`
trên máy cài SQL Server. Công cụ `tools/chuyen-du-lieu` đưa file đó vào hệ thống mới (PostgreSQL), rồi
**đối chiếu từng dòng** giữa hai bên. Chỉ cần Docker, không cần cài SQL Server hay Node.

## Chuyển những gì

| Phần mềm cũ | Hệ thống mới | Cách chuyển |
|---|---|---|
| Loại hàng (Classify) | Loại hàng | Gộp loại trùng tên (Bánh xe ×4, Bản mã, đá mài, Sơn sịt) |
| Sản phẩm | Sản phẩm | Giữ ID, giá, đơn vị (bỏ khoảng trắng thừa) |
| Khách hàng | Khách hàng | Giữ ID. **Nợ hiện tại = số nợ trên màn Khách hàng cũ**, ghi 1 dòng sổ nợ "Số dư chuyển từ phần mềm cũ" |
| Hóa đơn + dòng hàng | Hóa đơn | Giữ mã HD, ngày giờ, mọi số tiền như đã in. Xem / in lại được. Không ghi vào sổ nợ (nợ đã chốt ở trên) |
| Hóa đơn khách lẻ "còn nợ" | | Tính là **trả đủ** (hệ thống mới bắt khách lẻ trả đủ) |
| Số hóa đơn | | Hóa đơn mới tiếp tục số cũ (vd cũ đến HD00006356 → mới bắt đầu HD00006357) |
| Lịch sử trả nợ, tài khoản | — | Không chuyển (lịch sử không cộng lại khớp số nợ vì app cũ cho sửa nợ tay; tài khoản tạo lại ở màn Tài khoản) |
| Tồn kho | — | App cũ không có kho → tồn bắt đầu 0, làm **phiếu kiểm kê** sau khi chuyển |

Dòng của hóa đơn đã xóa ở app cũ bị bỏ. Sản phẩm đã xóa: dòng hóa đơn vẫn giữ tên.

## Ngày chuyển

1. **Ngừng dùng phần mềm cũ.** Đóng hết, mở lại 1 lần để nó tự sao lưu bản mới nhất, rồi đóng.
   Chép `D:\Sao lưu dữ liệu\BackupFile.bak` (trên máy cài SQL Server) ra USB / Drive.
2. **Chép file lên VPS:** `scp BackupFile.bak root@<IP VPS>:/opt/cuahang/chuyen/`
3. **Trên VPS** (hệ thống mới đã cài, đang chạy, **chưa nhập dữ liệu gì**):

   ```bash
   cd /opt/cuahang
   bash tools/chuyen-du-lieu/chuyen.sh tat-ca chuyen/BackupFile.bak
   ```

   Lệnh này: khôi phục file .bak trong một SQL Server tạm → xuất ra `chuyen/du-lieu-cu.json` → **sao lưu database
   hiện tại** (`backups/truoc-chuyen-du-lieu-*.dump`) → nạp → đối chiếu từng dòng → xóa SQL Server tạm.
4. Cuối cùng phải thấy **`KHOP HET`**. Báo cáo lưu ở `chuyen/bao-cao-chuyen-du-lieu-*.txt`: số dòng từng bảng,
   tổng nợ, tổng tiền, doanh thu từng tháng, và danh sách **bất thường trong dữ liệu cũ** (vd hóa đơn có tiền hàng
   khác tổng các dòng, số tiền khách trả gõ nhầm) để xem lại.
5. Mở web: kiểm tra vài khách quen (số nợ), vài hóa đơn gần nhất. Làm phiếu kiểm kê để nhập tồn kho.

SQL Server tạm cần **khoảng 2 GB RAM trống**. VPS nhỏ hơn: làm bước xuất trên máy khác có Docker
(`bash tools/chuyen-du-lieu/chuyen.sh xuat BackupFile.bak`), chép `du-lieu-cu.json` lên VPS rồi chạy
`bash tools/chuyen-du-lieu/chuyen.sh nap chuyen/du-lieu-cu.json`.

## An toàn

- Chỉ nạp vào database **mới**: đã có khách / sản phẩm / hóa đơn thì công cụ dừng, không ghi đè.
- Nạp trong 1 transaction: lỗi giữa chừng thì không ghi gì.
- Bước đối chiếu tự tính lại kết quả từ dữ liệu cũ, không dùng chung code với bước nạp → lỗi của bước nạp cũng bị bắt.
- Muốn làm lại từ đầu (vd chạy thử rồi chuyển thật): khôi phục bản `backups/truoc-chuyen-du-lieu-*.dump`
  (hướng dẫn khôi phục: `docs/trien-khai-vps.md` mục 6) rồi chạy lại.
- `du-lieu-cu.json` và `.bak` chứa dữ liệu khách hàng: chuyển xong thì xóa khỏi VPS (bản sao lưu database đã đủ).
