package com.shop.legacy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.ApiException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Nhap du lieu phan mem cu tu man web (khong can vao VPS).
 * Nap + doi chieu trong CUNG 1 transaction: doi chieu lech (hoac chi kiem tra thu) -> huy het, database khong doi.
 * Chi chay tren database chua co du lieu ban hang.
 */
@Service
public class LegacyImportService {
    /** Bang phai trong truoc khi nhap (tai khoan, cai dat, anh man dang nhap thi khong sao). */
    static final List<String> MUST_BE_EMPTY = List.of("category", "product", "customer", "orders", "debt_ledger",
            "supplier", "goods_receipt", "stock_movement", "stock_count");
    static final int MAX_UNZIPPED = 100 * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final LegacyImporter importer;
    private final LegacyVerifier verifier;
    private final ObjectMapper json;
    private final Object lock = new Object();

    public LegacyImportService(JdbcTemplate jdbc, PlatformTransactionManager tm, LegacyImporter importer,
                               LegacyVerifier verifier, ObjectMapper json) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(tm);
        this.importer = importer;
        this.verifier = verifier;
        this.json = json.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public record Status(boolean canImport, String blockedReason, Map<String, Integer> counts, LastImport last) {}

    public record LastImport(String at, String by, String source, String report) {}

    public record Result(boolean dryRun, boolean committed, boolean ok, String source, LegacyImporter.Stats stats,
                         LegacyVerifier.Report report) {}

    public Status status() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String t : MUST_BE_EMPTY) counts.put(t, jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Integer.class));
        String reason = blockedReason(counts);
        LastImport last = jdbc.query("SELECT created_at, created_by, source, report FROM legacy_import_log ORDER BY id DESC",
                rs -> rs.next() ? new LastImport(rs.getTimestamp(1).toLocalDateTime().toString(), rs.getString(2), rs.getString(3), rs.getString(4)) : null);
        return new Status(reason == null, reason, counts, last);
    }

    private static String blockedReason(Map<String, Integer> counts) {
        int customers = counts.get("customer"), products = counts.get("product"), orders = counts.get("orders");
        if (counts.values().stream().allMatch(n -> n == 0)) return null;
        return "Hệ thống đã có dữ liệu (" + customers + " khách, " + products + " sản phẩm, " + orders
                + " hóa đơn...). Chỉ nhập được vào hệ thống mới chưa dùng, để không ghi đè dữ liệu đang có.";
    }

    public Result run(byte[] body, boolean dryRun, String user) {
        LegacyData d = parse(body);
        synchronized (lock) { // 2 nguoi bam cung luc -> lan luot; lan sau thay database da co du lieu -> tu choi
            try {
                return tx.execute(st -> {
                    Map<String, Integer> counts = new LinkedHashMap<>();
                    for (String t : MUST_BE_EMPTY) counts.put(t, jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Integer.class));
                    String reason = blockedReason(counts);
                    if (reason != null) throw ApiException.conflict(reason);

                    LegacyImporter.Stats stats = importer.load(d, LocalDateTime.now());
                    LegacyVerifier.Report report = verifier.verify(d);
                    boolean commit = !dryRun && report.ok();
                    if (commit) {
                        jdbc.update("INSERT INTO legacy_import_log (created_by, source, report) VALUES (?, ?, ?)",
                                user, source(d), text(report));
                    } else st.setRollbackOnly(); // kiem tra thu, hoac lech -> khong ghi gi
                    return new Result(dryRun, commit, report.ok(), source(d), stats, report);
                });
            } catch (DataAccessException e) {
                Throwable c = e.getMostSpecificCause();
                throw ApiException.badRequest("Không nạp được dữ liệu (chưa ghi gì): " + (c.getMessage() == null ? c : c.getMessage().lines().findFirst().orElse("")));
            }
        }
    }

    private LegacyData parse(byte[] body) {
        if (body == null || body.length == 0) throw ApiException.badRequest("Chưa chọn file");
        LegacyData d;
        try (InputStream in = open(body)) {
            d = json.readValue(in, LegacyData.class);
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest("File không đúng: cần file du-lieu-cu.json (hoặc .json.gz) tạo từ BackupFile.bak");
        }
        if (d == null || d.classify() == null || d.products() == null || d.customers() == null || d.orders() == null || d.bills() == null)
            throw ApiException.badRequest("File thiếu dữ liệu: cần file du-lieu-cu.json tạo từ BackupFile.bak");
        // kiem tra so bo: thieu ma / trung ma -> bao som, ro rang
        Set<String> codes = new HashSet<>();
        for (var o : d.orders()) {
            if (o.id() == null || o.date() == null) throw ApiException.badRequest("Hóa đơn thiếu mã hoặc ngày");
            if (!codes.add(o.id())) throw ApiException.badRequest("Trùng mã hóa đơn " + o.id());
        }
        if (d.products().stream().anyMatch(p -> p.id() == null) || d.customers().stream().anyMatch(c -> c.id() == null)
                || d.classify().stream().anyMatch(c -> c.id() == null) || d.bills().stream().anyMatch(b -> b.id() == null))
            throw ApiException.badRequest("File có dòng thiếu ID");
        return d;
    }

    /** Nhan ca file .json va .json.gz (trinh duyet nen truoc khi gui -> nhe, nhanh tren 4G). */
    private static InputStream open(byte[] body) throws IOException {
        InputStream raw = new ByteArrayInputStream(body);
        if (body.length > 2 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b)
            return new LimitedStream(new GZIPInputStream(raw), MAX_UNZIPPED);
        return raw;
    }

    private static String source(LegacyData d) {
        return (d.nguon() == null ? "Phần mềm cũ" : d.nguon()) + (d.xuatLuc() == null ? "" : ", xuất lúc " + d.xuatLuc());
    }

    static String text(LegacyVerifier.Report r) {
        StringBuilder b = new StringBuilder();
        for (var l : r.summary()) b.append(l.label()).append(": ").append(l.before()).append(" -> ").append(l.after()).append('\n');
        if (!r.anomalies().isEmpty()) {
            b.append("\nBất thường trong dữ liệu cũ (đã chuyển nguyên trạng):\n");
            r.anomalies().forEach(a -> b.append("- ").append(a).append('\n'));
        }
        b.append(r.ok() ? "\nKHỚP HẾT" : "\nLỆCH " + r.errorCount() + " chỗ");
        return b.toString();
    }

    /** Chan file nen "bom" (vai KB giai nen ra vai GB). */
    private static final class LimitedStream extends java.io.FilterInputStream {
        private long left;

        LimitedStream(InputStream in, long max) { super(in); left = max; }

        private void take(long n) throws IOException { if ((left -= n) < 0) throw new IOException("File quá lớn"); }

        @Override public int read() throws IOException { int c = super.read(); if (c >= 0) take(1); return c; }

        @Override public int read(byte[] b, int off, int len) throws IOException { int n = super.read(b, off, len); if (n > 0) take(n); return n; }
    }
}
