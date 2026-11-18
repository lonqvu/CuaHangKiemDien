-- Anh nen man dang nhap (quan tri doi o man Tai khoan). Luu trong DB (1 dong) -> nam luon trong ban sao luu hang ngay.
CREATE TABLE login_image (
    id           SMALLINT PRIMARY KEY,
    content_type VARCHAR(40)  NOT NULL,
    data         BYTEA        NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    updated_by   VARCHAR(100)
);
