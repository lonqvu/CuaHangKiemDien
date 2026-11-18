-- Xac thuc 2 lop (TOTP, app Google Authenticator) cho tai khoan.
-- totp_secret: khoa bi mat da MA HOA (AES-GCM, khoa suy tu JWT_SECRET) -> lo file sao luu cung khong lay duoc khoa.
-- totp_last_step: buoc 30 giay cua ma vua dung -> mot ma khong dung lai duoc lan 2.
-- recovery_codes: ma du phong (dung 1 lan, khi mat dien thoai), luu ban bam BCrypt, moi dong 1 ma.
ALTER TABLE app_user ADD COLUMN totp_secret VARCHAR(255);
ALTER TABLE app_user ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN totp_last_step BIGINT;
ALTER TABLE app_user ADD COLUMN recovery_codes TEXT;
