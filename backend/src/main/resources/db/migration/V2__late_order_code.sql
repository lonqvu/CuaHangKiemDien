-- Ma hoa don (HDxxxxxxxx) chi cap khi chot: hoa don nhap chua co ma, so hoa don lien tuc.
ALTER TABLE orders ALTER COLUMN code DROP NOT NULL;

-- So no phan trang theo id moi nhat truoc
CREATE INDEX idx_ledger_customer_id ON debt_ledger(customer_id, id);

-- Ghi chu so no cu viet khong dau -> co dau (cot Ghi chu hien cho nguoi dung)
UPDATE debt_ledger SET note = REPLACE(note, 'Thanh toan hoa don ', 'Thanh toán hóa đơn ') WHERE note LIKE 'Thanh toan hoa don %';
UPDATE debt_ledger SET note = REPLACE(note, 'Huy hoa don ', 'Hủy hóa đơn ') WHERE note LIKE 'Huy hoa don %';
UPDATE debt_ledger SET note = REPLACE(note, 'Hoa don ', 'Hóa đơn ') WHERE note LIKE 'Hoa don %';
UPDATE debt_ledger SET note = 'Nợ ban đầu' WHERE note = 'No ban dau';
UPDATE debt_ledger SET note = 'Điều chỉnh nợ' WHERE note = 'Dieu chinh no';
UPDATE debt_ledger SET note = 'Khách trả nợ' WHERE note = 'Khach tra no';
