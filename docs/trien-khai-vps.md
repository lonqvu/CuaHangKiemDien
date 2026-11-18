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
docker compose -f docker-compose.vps.yml ps          # 6 dịch vụ: db, backend, frontend, caddy, backup, offsite = running
docker compose -f docker-compose.vps.yml logs -f caddy   # thấy "certificate obtained" là đã có HTTPS
```

- Lần đầu build mất khoảng 5–10 phút.
- Mở `https://<DOMAIN>` và đăng nhập `admin` / `ADMIN_PASSWORD`.
- Vào **Đổi mật khẩu** đổi ngay, **bật xác thực 2 lớp** (mục 5), rồi tạo tài khoản cho nhân viên ở **Tài khoản**.

## 5. Dùng trên điện thoại

Mở `https://<DOMAIN>` rồi cài thành ứng dụng: có biểu tượng "Kiểm Diên" và mở toàn màn hình.

- **iPhone (Safari):** nút Chia sẻ → **Thêm vào MH chính**.
- **Android (Chrome):** menu ⋮ → **Thêm vào màn hình chính** (hoặc **Cài đặt ứng dụng**).

Bảo mật khi mở ra Internet:
- Đăng nhập sai **5 lần trong 15 phút** (cùng máy, cùng tài khoản) sẽ bị tạm khóa 15 phút.
- Một máy thử nhiều tài khoản: bị khóa sau 20 lần sai.
- Màn đổi mật khẩu cũng dùng chung giới hạn này.
- Kẻ xấu ở máy khác **không** khóa được tài khoản của bạn.

### Ghi nhớ đăng nhập, đăng xuất máy khác

- **Ghi nhớ đăng nhập trên máy này (7 ngày):** tích ở màn đăng nhập thì 7 ngày không phải đăng nhập lại; lần sau tên đăng nhập được điền sẵn
  (không bao giờ lưu mật khẩu). Không tích: phiên 8 giờ, không nhớ tên. Máy dùng chung thì **đừng tích**.
- **Đổi mật khẩu** thì mọi máy khác đang đăng nhập tài khoản đó **tự bị đăng xuất**, máy đang đổi vẫn giữ đăng nhập.
  Quản trị đặt lại mật khẩu hoặc tắt 2 lớp giúp cũng đăng xuất mọi máy của người đó.
- **Mất điện thoại / nghi lộ mật khẩu:** **Đổi mật khẩu** → **Phiên đăng nhập** → **Đăng xuất mọi thiết bị khác**.
- Đổi số ngày (mặc định 7): trong `.env` thêm `JWT_REMEMBER_DAYS=…` (ghi nhớ đăng nhập), `TRUST_DEVICE_DAYS=…` (không hỏi mã 2 lớp).

### Xác thực 2 lớp cho tài khoản quản trị (nên bật)

Đăng nhập cần thêm **mã 6 số** từ ứng dụng trên điện thoại, nên lộ mật khẩu thì người khác vẫn không vào được.
Tài khoản quản trị chưa bật sẽ thấy dòng nhắc ở đầu trang.

1. Cài **Google Authenticator** hoặc **Microsoft Authenticator** trên điện thoại.
2. Trong web: **Đổi mật khẩu** → **Xác thực 2 lớp** → **Bật xác thực 2 lớp** → nhập mật khẩu.
3. Trong ứng dụng: **+** → **Quét mã QR** → quét mã trên màn hình → nhập mã 6 số đang hiện → **Xác nhận, bật**.
4. Màn hình hiện **8 mã dự phòng**: tải về hoặc in ra, cất chỗ an toàn. Mất điện thoại thì dùng 1 mã thay cho mã 6 số (mỗi mã 1 lần).

Từ lần sau, đăng nhập = mật khẩu + mã 6 số đang hiện trên ứng dụng. Ở bước nhập mã có ô **"Không hỏi mã trên máy này 7 ngày"**:
tích trên máy riêng của mình (điện thoại, máy ở nhà) thì 7 ngày không phải nhập mã trên máy đó; máy khác vẫn bị hỏi.
"Đăng xuất mọi thiết bị khác", đổi mật khẩu, hoặc cài lại 2 lớp đều làm các máy đã tin cậy phải nhập mã lại.

- **Đổi điện thoại / mất điện thoại:** đăng nhập bằng 1 mã dự phòng, tắt rồi bật lại 2 lớp với điện thoại mới.
  Hết mã dự phòng thì nhờ quản trị khác vào **Tài khoản** → **Tắt 2 lớp**.
- **Đổi `JWT_SECRET`** trong `.env` thì tài khoản đã bật 2 lớp phải cài lại, vì khóa 2 lớp được mã hóa bằng nó. Không đổi thì không sao.

## 6. Sao lưu và khôi phục

Dịch vụ `backup` tự sao lưu lúc khởi động và sau đó mỗi 24 giờ vào `/opt/cuahang/backups/shop-YYYYMMDD-HHMM.dump`,
giữ 30 ngày gần nhất trên VPS.

### Tự chép sao lưu ra ngoài VPS (Google Drive), có mã hóa

VPS hỏng hoặc bị xóa thì mất luôn bản sao lưu trên đó. Dịch vụ `offsite` (dùng **rclone**) cứ 6 giờ chép các bản
sao lưu mới lên Google Drive (hoặc OneDrive, Dropbox…), và giữ 90 ngày gần nhất trên Drive. Cấu hình **một lần**:

**Bước 1: lấy quyền Google Drive trên máy tính** (máy có trình duyệt). Tải rclone tại rclone.org/downloads, giải nén, mở cmd/Terminal trong thư mục đó:

```bash
rclone authorize "drive" "eyJzY29wZSI6ImRyaXZlLmZpbGUifQ"
```

Trình duyệt mở ra → đăng nhập Google → **Cho phép**. Cửa sổ cmd in ra một đoạn bắt đầu bằng `{"access_token":...}`: chép **cả đoạn** đó.
Quyền `drive.file` chỉ cho rclone thấy file **do nó tạo**, không đọc được các file khác trong Drive của bạn.
Nếu lệnh trên báo lỗi: làm bước 2 trước, đến câu "Use web browser" chọn `n`. rclone sẽ in ra đúng một lệnh `rclone authorize ...`; chạy lệnh đó trên máy tính.

**Bước 2: tạo cấu hình trên VPS:**

```bash
cd /opt/cuahang
docker run --rm -it -v /opt/cuahang/deploy/rclone:/config/rclone rclone/rclone:1.68 config
```

Trả lời lần lượt:
- `n` (tạo mới) → tên: `gdrive` → loại: `drive` → `client_id`, `client_secret`: Enter (bỏ trống) → `scope`: chọn `drive.file`
- Các câu tiếp theo Enter (mặc định), đến **"Use web browser to automatically authenticate"** chọn `n` → dán đoạn `{"access_token":...}` ở bước 1
- `Configure this as a Shared Drive`: `n` → `y` (lưu)

**Bước 3 (nên làm): thêm lớp mã hóa.** Google không đọc được dữ liệu khách hàng; ai lấy được file cũng không mở được nếu không có mật khẩu. Vẫn trong `config`:
- `n` → tên: `gdrive-mahoa` → loại: `crypt` → `remote`: `gdrive:CuaHangSaoLuu`
- `filename_encryption`: `standard` → `directory_name_encryption`: `true`
- `password`: `y` → tự đặt một **mật khẩu mạnh** → `password2`: `g` (tự tạo) → `y` (lưu) → `q` (thoát)

**Ghi mật khẩu mã hóa (và password2) ra giấy, cất kỹ.** Mất VPS mà quên mật khẩu này thì bản trên Drive **không mở được**.

**Bước 4: bật.** Trong `.env` thêm `OFFSITE_REMOTE=gdrive-mahoa:` (không mã hóa thì `gdrive:CuaHangSaoLuu`), rồi:

```bash
docker compose -f docker-compose.vps.yml up -d offsite
docker compose -f docker-compose.vps.yml logs -f offsite   # sau khoảng 1 phút thấy "da chep sao luu len [gdrive-mahoa:], hien co N ban"
```

Kiểm tra trên Drive: có thư mục `CuaHangSaoLuu`. Nếu có mã hóa thì tên file là chuỗi ký tự lạ, như vậy là đúng.

**Lấy bản sao lưu từ Drive về** (VPS mới, hoặc máy khác đã có `deploy/rclone/rclone.conf` cũ, giữ file này cẩn thận):

```bash
docker run --rm -v /opt/cuahang/deploy/rclone:/config/rclone -v /opt/cuahang/backups:/backups rclone/rclone:1.68 copy gdrive-mahoa: /backups
```

Sau đó khôi phục như hướng dẫn bên dưới.

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
| Không chép được lên Drive | `logs offsite`. Hay gặp: token Google hết hạn → làm lại bước 1–2 mục 6 (chọn `e` sửa remote `gdrive`) |
| Mất điện thoại có mã 2 lớp | Đăng nhập bằng mã dự phòng, hoặc nhờ quản trị khác **Tài khoản** → **Tắt 2 lớp** |

## 9. Giới hạn thiết bị: chỉ cho một số IP vào web

Mặc định, ai có địa chỉ web cũng **mở được trang đăng nhập** (vẫn phải có tài khoản; đoán sai mật khẩu sẽ bị khóa).
Muốn chỉ máy ở một số mạng vào được, dùng danh sách IP:

1. Xem IP mạng hiện tại: mở `https://ifconfig.me` trên máy hoặc điện thoại (đang dùng mạng đó).
2. Trong `.env` trên VPS: `ALLOWED_IPS=113.161.5.6 27.72.10.20`. Cách nhau bằng dấu cách, ghi được cả dải, vd `14.0.0.0/8`. Để trống là cho mọi IP.
3. Áp dụng:

```bash
docker compose -f docker-compose.vps.yml up -d caddy
```

Máy ngoài danh sách sẽ nhận trang *"Thiết bị này không được phép truy cập."* (lỗi 403).

Lưu ý: điện thoại dùng **4G** và mạng nhà thường **đổi IP** (mất sóng, modem khởi động lại). Khi IP đổi mà chưa có trong
danh sách thì máy đó bị chặn, phải xem lại IP và sửa `ALLOWED_IPS`. Cách này hợp nhất với mạng có **IP tĩnh**.
