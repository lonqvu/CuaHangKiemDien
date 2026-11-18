# Triển khai lên VPS (dùng điện thoại mọi lúc, mọi nơi)

Kết quả: mở `https://<tên miền>` trên điện thoại / máy tính ở bất cứ đâu, có HTTPS (ổ khóa xanh),
dữ liệu tự sao lưu mỗi ngày.

```
Internet ──443──> caddy (HTTPS tự động) ──> frontend (nginx + giao diện) ──/api──> backend ──> PostgreSQL
                                                                         backup: sao lưu mỗi ngày ─┘
```
Chỉ cổng 80/443 mở ra ngoài. Backend và database **không** mở cổng nên không ai truy cập thẳng được.

## 1. Chuẩn bị

| Cần | Gợi ý |
|---|---|
| VPS | Ubuntu 22.04/24.04, **2 GB RAM** trở lên (1 GB thì phải thêm swap, xem mục 7), 20 GB ổ |
| Tên miền (nên có) | vd `banhang.nhomsatkiemdien.vn`. Tạo bản ghi **A** trỏ về IP VPS. |
| Chưa có tên miền | Dùng `<IP VPS>.sslip.io`, vd `103.20.30.40.sslip.io`: vẫn có HTTPS, không cần mua gì |

## 2. Cài Docker trên VPS (làm 1 lần)

```bash
ssh root@<IP VPS>
curl -fsSL https://get.docker.com | sh
# tường lửa: chỉ mở SSH + web
ufw allow OpenSSH && ufw allow 80 && ufw allow 443 && ufw --force enable
```

## 3. Lấy mã nguồn và cấu hình

```bash
git clone https://github.com/lonqvu/CuaHangKiemDien.git /opt/cuahang
cd /opt/cuahang
cp .env.example .env
nano .env
```

Trong `.env`:

```
DB_PASSWORD=<openssl rand -base64 24>
JWT_SECRET=<openssl rand -base64 48>
ADMIN_PASSWORD=<mật khẩu admin lần đầu, mạnh>
DOMAIN=banhang.nhomsatkiemdien.vn        # hoặc 103.20.30.40.sslip.io
```

- Chạy `openssl rand -base64 48` để lấy chuỗi ngẫu nhiên, rồi dán vào.
- **Không** đưa file `.env` lên git (đã có trong `.gitignore`).

## 4. Chạy

```bash
docker compose -f docker-compose.vps.yml up -d --build
docker compose -f docker-compose.vps.yml ps          # 5 dịch vụ: db, backend, frontend, caddy, backup = running
docker compose -f docker-compose.vps.yml logs -f caddy   # thấy "certificate obtained" là đã có HTTPS
```

- Lần đầu build mất khoảng 5–10 phút.
- Mở `https://<DOMAIN>` và đăng nhập `admin` / `ADMIN_PASSWORD`.
- Vào **Đổi mật khẩu** đổi ngay, rồi tạo tài khoản cho nhân viên ở **Tài khoản**.

## 5. Dùng trên điện thoại

Mở `https://<DOMAIN>` rồi cài thành ứng dụng: có biểu tượng "Kiểm Diên" và mở toàn màn hình.

- **iPhone (Safari):** nút Chia sẻ → **Thêm vào MH chính**.
- **Android (Chrome):** menu ⋮ → **Thêm vào màn hình chính** (hoặc **Cài đặt ứng dụng**).

Bảo mật khi mở ra Internet:
- Đăng nhập sai **5 lần trong 15 phút** (cùng máy, cùng tài khoản) sẽ bị tạm khóa 15 phút.
- Một máy thử nhiều tài khoản: bị khóa sau 20 lần sai.
- Màn đổi mật khẩu cũng dùng chung giới hạn này.
- Kẻ xấu ở máy khác **không** khóa được tài khoản của bạn.

## 6. Sao lưu và khôi phục

Dịch vụ `backup` tự sao lưu lúc khởi động và sau đó mỗi 24 giờ vào `/opt/cuahang/backups/shop-YYYYMMDD-HHMM.dump`,
giữ 30 ngày gần nhất. **Nên chép định kỳ ra ngoài VPS** (máy nhà, Google Drive…), vì VPS hỏng thì mất luôn bản sao lưu:

```bash
scp root@<IP VPS>:/opt/cuahang/backups/*.dump ./sao-luu/
```

Sao lưu ngay (trước khi cập nhật):

```bash
docker compose -f docker-compose.vps.yml exec db pg_dump -U shop -d shop -Fc > backups/truoc-cap-nhat.dump
```

Khôi phục (ghi đè dữ liệu hiện tại):

```bash
docker compose -f docker-compose.vps.yml exec -T db pg_restore -U shop -d shop --clean --if-exists < backups/shop-20261118-0941.dump
docker compose -f docker-compose.vps.yml restart backend
```

**Chuyển dữ liệu đang dùng ở máy nhà lên VPS:**
1. Trên máy nhà, xuất dữ liệu: `docker compose exec db pg_dump -U shop -d shop -Fc > shop.dump`
2. Chép file lên VPS: `scp shop.dump root@<IP VPS>:/opt/cuahang/backups/`
3. Trên VPS, chạy lệnh khôi phục ở trên với file `shop.dump`.

## 7. Cập nhật bản mới

```bash
cd /opt/cuahang
git pull
docker compose -f docker-compose.vps.yml up -d --build
```

- Database tự nâng cấp (Flyway).
- Điện thoại nhận bản mới ngay khi mở lại app, vì `index.html` không bị cache.

VPS 1 GB RAM: build có thể thiếu bộ nhớ. Thêm 2 GB swap (làm 1 lần):

```bash
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile && echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

## 8. Sự cố thường gặp

| Hiện tượng | Kiểm tra |
|---|---|
| Không vào được trang | `ps`: đủ 5 dịch vụ chưa? Tường lửa / nhà cung cấp VPS đã mở cổng 80, 443 chưa? |
| Báo lỗi chứng chỉ HTTPS | Bản ghi A của tên miền đã trỏ đúng IP chưa (`ping <DOMAIN>`)? Xem `logs caddy` |
| "Nhập sai mật khẩu quá nhiều lần" | Chờ 15 phút, hoặc `docker compose -f docker-compose.vps.yml restart backend` |
| Quên mật khẩu admin | Một quản trị khác đặt lại ở **Tài khoản**. Nếu không còn ai: liên hệ người cài đặt để đặt lại trong database |
| Xem lỗi máy chủ | `docker compose -f docker-compose.vps.yml logs --tail 200 backend` |

## 9. Giới hạn thiết bị: chỉ máy tính nhà và điện thoại của mình vào được

Mặc định, ai có địa chỉ web cũng **mở được trang đăng nhập** (vẫn phải có tài khoản; đoán sai mật khẩu sẽ bị khóa).
Muốn người ngoài **không thấy cả trang web**, chọn một trong hai cách:

| | A. Danh sách IP (`ALLOWED_IPS`) | B. Tailscale (khuyên dùng) |
|---|---|---|
| Ai vào được | Máy có IP nằm trong danh sách | Máy đã cài Tailscale, đăng nhập tài khoản của bạn |
| Điện thoại dùng 4G | ❌ IP 4G đổi liên tục, bị chặn | ✅ Ở đâu cũng vào được |
| Mạng nhà đổi IP (modem khởi động lại) | ❌ Phải sửa lại danh sách | ✅ Không ảnh hưởng |
| Hợp với | Văn phòng có **IP tĩnh** | Cửa hàng, gia đình, đi lại nhiều |

### A. Danh sách IP

1. Xem IP mạng hiện tại: mở `https://ifconfig.me` trên máy hoặc điện thoại (đang dùng mạng đó).
2. Trong `.env` trên VPS: `ALLOWED_IPS=113.161.5.6 27.72.10.20`. Cách nhau bằng dấu cách, ghi được cả dải, vd `14.0.0.0/8`. Để trống là cho mọi IP.
3. Áp dụng:

```bash
docker compose -f docker-compose.vps.yml up -d caddy
```

Máy ngoài danh sách sẽ nhận trang *"Thiết bị này không được phép truy cập."* (lỗi 403).

### B. Tailscale (mạng riêng giữa các thiết bị của bạn)

1. Tạo tài khoản miễn phí tại tailscale.com, cài app **Tailscale** trên máy tính nhà và điện thoại, đăng nhập cùng tài khoản.
2. Trên VPS, cài Tailscale rồi đăng nhập theo link nó in ra:

```bash
curl -fsSL https://tailscale.com/install.sh | sh
tailscale up
```

3. Trong trang quản trị Tailscale → **DNS**: bật **MagicDNS** và **HTTPS Certificates**. Tên VPS sẽ có dạng `cuahang.tail1234.ts.net`.
4. Trong `.env`: `DOMAIN=cuahang.tail1234.ts.net`. Sau đó chạy app ở chế độ chỉ trong mạng riêng và mở HTTPS qua Tailscale:

```bash
docker compose -f docker-compose.vps.yml -f deploy/tailscale.yml up -d --build
tailscale serve --bg --https=443 http://127.0.0.1:8081
```

5. Đóng cổng web ra Internet, chỉ để SSH:

```bash
ufw delete allow 80 && ufw delete allow 443
```

6. Trên điện thoại (đã bật Tailscale) mở `https://cuahang.tail1234.ts.net`, rồi **Thêm vào màn hình chính** (mục 5).

Thêm hoặc bớt thiết bị: vào trang quản trị Tailscale → **Machines**. Mất điện thoại thì xóa máy đó ở đây là nó hết vào được.
