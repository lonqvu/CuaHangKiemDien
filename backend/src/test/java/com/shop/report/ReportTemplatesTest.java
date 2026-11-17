package com.shop.report;

import com.shop.report.jasper.BillReportData;
import com.shop.customer.DebtStatementService;
import com.shop.report.jasper.JasperReportService;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JasperCompileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportTemplatesTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void billData_groupsSheetLinesLikeOldSql() {
        List<BillReportData.Line> lines = List.of(
                new BillReportData.Line(21, 5L, "Tôn lạnh", "m", d("4.700"), 2, d("1.080"), d("98000"), d("10.152"), d("994896")),
                new BillReportData.Line(22, 5L, "Tôn lạnh", "m", d("4.500"), 2, d("1.080"), d("98000"), d("9.720"), d("952560")),
                new BillReportData.Line(23, 5L, "Tôn lạnh", "m", d("3"), 1, d("1.08"), d("98000"), d("3.240"), d("317520")),
                new BillReportData.Line(24, 3L, "Sắt hộp", "cây", d("6"), 0, d("0"), d("268000"), d("6"), d("1608000")));
        List<Map<String, ?>> rows = BillReportData.rows(header(), lines);
        assertEquals(3, rows.size());
        // ORDER BY Products_Name DESC: "Tôn lạnh" truoc "Sắt hộp"; cung SP thi so tam tang dan
        assertEquals("3", rows.get(0).get("Amount_List"));
        assertEquals("4.5 + 4.7", rows.get(1).get("Amount_List"));
        assertEquals(2, rows.get(1).get("Line_Count"));
        assertEquals(4, rows.get(1).get("Bill_SoTam_Total"));
        assertEquals(0, d("1947456").compareTo((BigDecimal) rows.get(1).get("Bill_IntoMoney")));
        assertEquals("Sắt hộp", rows.get(2).get("Products_Name"));
        assertEquals(3L, rows.get(2).get("STT"));
    }

    @Test
    void templatesRenderPdf() {
        JasperReportService svc = new JasperReportService("en-US");
        byte[] bill = svc.invoice(header(), List.of(
                new BillReportData.Line(1, 1L, "Nhôm Xingfa hệ 55", "cây", d("2"), 0, d("0"), d("485000"), d("2"), d("970000"))),
                JasperReportService.Format.PDF);
        assertTrue(new String(bill, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
        var lines = List.of(
                new DebtStatementService.Line(1L, LocalDateTime.of(2026, 11, 1, 8, 0), "Điều chỉnh: Nợ ban đầu", d("1000000"), null, d("1000000"), false),
                new DebtStatementService.Line(2L, LocalDateTime.of(2026, 11, 3, 9, 0), "Thu nợ PT000001", null, d("300000"), d("700000"), true));
        byte[] receipt = svc.debtDoc(new DebtStatementService.Doc("PHIẾU THU NỢ", "Số: PT000001", LocalDateTime.of(2026, 11, 3, 9, 0),
                1L, "Anh Tuấn", "Thôn Đông", "0912", "Nguyễn Thị Thu", "Tiền mặt", true, "Thu nhầm số tiền", lines, List.of(
                new DebtStatementService.Total("Nợ trước khi thu", d("1000000"), false),
                new DebtStatementService.Total("Số tiền thu (tiền mặt)", d("300000"), false),
                new DebtStatementService.Total("Còn nợ", d("700000"), true))), JasperReportService.Format.PDF);
        assertTrue(new String(receipt, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
    }

    /** Anh gui Zalo: PNG hop le, zoom 2 (rong 2 x 595), cat phan trang thua cuoi trang (thap hon 2 x 1000). */
    @Test
    void templatesRenderPngForZalo() throws Exception {
        JasperReportService svc = new JasperReportService("en-US");
        byte[] png = svc.invoice(header(), List.of(
                new BillReportData.Line(1, 1L, "Nhôm Xingfa hệ 55", "cây", d("2"), 0, d("0"), d("485000"), d("2"), d("970000"))),
                JasperReportService.Format.PNG);
        java.nio.file.Files.write(java.nio.file.Path.of("target", "invoice-sample.png"), png); // xem bang mat khi can
        var img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        assertNotNull(img, "PNG doc duoc");
        assertTrue(Math.abs(img.getWidth() - 1190) <= 2, "rong ~ 2 x 595, la " + img.getWidth()); // Jasper lam tron +1px
        assertTrue(img.getHeight() < 2000, "da cat phan trang thua, cao " + img.getHeight());
    }

    /** Anh phieu thu no gui Zalo (mau PhieuThuNo): PNG hop le, cung zoom 2. */
    @Test
    void receiptRendersPngForZalo() throws Exception {
        JasperReportService svc = new JasperReportService("en-US");
        var lines = List.of(new DebtStatementService.Line(1L, LocalDateTime.of(2026, 11, 3, 9, 0), "Thu nợ PT000001", null, d("300000"), d("0"), true));
        byte[] png = svc.debtDoc(new DebtStatementService.Doc("PHIẾU THU NỢ", "Số: PT000001", LocalDateTime.of(2026, 11, 3, 9, 0),
                1L, "Khách lẻ", null, null, "Thu", "Tiền mặt", false, null, lines, List.of(
                new DebtStatementService.Total("Nợ trước khi thu", d("300000"), false),
                new DebtStatementService.Total("Số tiền thu (tiền mặt)", d("300000"), false),
                new DebtStatementService.Total("Còn nợ", d("0"), true))), JasperReportService.Format.PNG);
        java.nio.file.Files.write(java.nio.file.Path.of("target", "receipt-sample.png"), png);
        var img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        assertNotNull(img);
        assertTrue(Math.abs(img.getWidth() - 1190) <= 2, "rong " + img.getWidth());
    }

    /** Sua file .jrxml xong thi chay: mvn test -Dtest=ReportTemplatesTest -Dreports.compile=true de bien dich lai .jasper */
    @Test
    @EnabledIfSystemProperty(named = "reports.compile", matches = "true")
    void compileTemplates() throws Exception {
        DefaultJasperReportsContext.getInstance().setProperty("net.sf.jasperreports.compiler.class",
                "net.sf.jasperreports.engine.design.JRJdk13Compiler");
        for (String n : new String[] {"Bill", "PhieuThuNo"}) {
            String base = "src/main/resources/reports/" + n;
            JasperCompileManager.compileReportToFile(base + ".jrxml", base + ".jasper");
        }
    }

    private static BillReportData.Header header() {
        return new BillReportData.Header("HD00000001", LocalDateTime.of(2026, 11, 15, 10, 15), "Khách lẻ", null, null,
                d("0"), d("970000"), d("970000"), d("1000000"), d("0"), d("0"), "Long", null);
    }
}
