-- Phieu thu no + sao ke cong no in tu so cai:
--  receipt_no : so phieu thu (PT000001...), chi co o lan thu no tai man Khach hang
--  method     : CASH (tien mat) / TRANSFER (chuyen khoan)
--  created_by : nguoi thao tac (nguoi thu, nguoi sua no...)
--  reversal_of: dong VOID (huy phieu thu) tro toi dong PAYMENT bi huy; moi phieu chi huy duoc 1 lan
ALTER TABLE debt_ledger ADD COLUMN receipt_no VARCHAR(20);
ALTER TABLE debt_ledger ADD COLUMN method VARCHAR(20);
ALTER TABLE debt_ledger ADD COLUMN created_by VARCHAR(100);
ALTER TABLE debt_ledger ADD COLUMN reversal_of BIGINT REFERENCES debt_ledger(id);
CREATE UNIQUE INDEX ux_ledger_receipt_no ON debt_ledger(receipt_no);
CREATE UNIQUE INDEX ux_ledger_reversal_of ON debt_ledger(reversal_of);
CREATE SEQUENCE receipt_no_seq START WITH 1 INCREMENT BY 1;
