-- Phien dang nhap: moi token mang so phien ban cua tai khoan. Tang so nay = moi token cu (moi may) het hieu luc:
-- doi mat khau, quan tri dat lai mat khau / tat 2 lop giup, "Dang xuat moi thiet bi khac".
-- Cung lam mat hieu luc "tin cay may nay" cua xac thuc 2 lop.
ALTER TABLE app_user ADD COLUMN token_version INT NOT NULL DEFAULT 0;
