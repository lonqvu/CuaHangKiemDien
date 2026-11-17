-- Huy hoa don sau khi khach da tra bot/tra het no cua chinh hoa don do: no chi ve 0 (khong am),
-- phan con lai la tien cua hang phai tra lai khach -> luu tren hoa don de tra cuu.
ALTER TABLE orders ADD COLUMN refund_amount NUMERIC(18,2) NOT NULL DEFAULT 0;
