package com.shop.report.jasper;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sinh PDF bang chinh cac mau JasperReports cua app cu (src/main/resources/reports/*.jrxml).
 * File .jasper la ban da bien dich san (JasperReports 5.5.0) de khong phai compile luc chay.
 * Sua .jrxml thi bien dich lai: xem ReportTemplatesTest (chay voi -Dreports.compile=true).
 */
@Service
public class JasperReportService {
    private final Map<String, JasperReport> cache = new ConcurrentHashMap<>();
    private final Locale locale;

    /** Dinh dang so/ngay tren ban in. Mac dinh en-US vi may chay app cu in so dang 1,234,567. */
    public JasperReportService(@Value("${app.report.locale:en-US}") String locale) {
        this.locale = Locale.forLanguageTag(locale);
    }

    private JasperReport template(String name) {
        return cache.computeIfAbsent(name, n -> {
            try (InputStream in = new ClassPathResource("reports/" + n + ".jasper").getInputStream()) {
                return (JasperReport) JRLoader.loadObject(in);
            } catch (IOException | JRException e) {
                throw new IllegalStateException("Khong nap duoc mau in " + n, e);
            }
        });
    }

    private static InputStream image(String file) {
        try {
            return new ClassPathResource("reports/images/" + file).getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private byte[] render(String name, Map<String, Object> params, List<Map<String, ?>> rows) {
        params.put(JRParameter.REPORT_LOCALE, locale);
        try {
            JasperPrint print = JasperFillManager.fillReport(template(name), params, new JRMapCollectionDataSource((Collection) rows));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new IllegalStateException("Loi khi in " + name, e);
        }
    }

    /** Hoa don ban hang (Bill.jrxml). */
    public byte[] invoice(BillReportData.Header header, List<BillReportData.Line> lines) {
        Map<String, Object> p = new HashMap<>();
        p.put("ORDER_ID", header.orderCode());
        p.put("LOGO", image("logo.jpg"));
        p.put("QR", image("qr.jpg"));
        return render("Bill", p, BillReportData.rows(header, lines));
    }

    /**
     * Phieu thu no / sao ke cong no (PhieuThuNo.jrxml). Dau trang giong mau bien lai cu; bang 5 cot tu so cong no.
     * Toi da 4 dong tong ket, dong cuoi in dam (Con no / So du cuoi ky) -> dien tu duoi len.
     */
    public byte[] debtDoc(com.shop.customer.DebtStatementService.Doc d) {
        Map<String, Object> p = new HashMap<>();
        p.put("TITLE", d.title());
        p.put("DOC_NO", d.docNo());
        p.put("DOC_DATE", d.date() == null ? null : java.sql.Timestamp.valueOf(d.date()));
        p.put("CUSTOMER_NAME", d.customerName());
        p.put("ADDRESS", d.address());
        p.put("PHONE", d.phone());
        p.put("COLLECTOR", d.collector());
        p.put("METHOD", d.method());
        p.put("VOIDED", d.voided());
        p.put("VOID_REASON", d.voidReason());
        p.put("SIGN_RIGHT", d.collector() == null ? "Người lập" : "Người thu tiền");
        List<com.shop.customer.DebtStatementService.Total> t = d.totals();
        for (int i = 0; i < t.size(); i++) {
            int slot = 4 - t.size() + 1 + i;
            p.put("T" + slot + "_LABEL", t.get(i).label());
            p.put("T" + slot, t.get(i).amount());
        }
        List<Map<String, ?>> rows = new ArrayList<>();
        for (var l : d.lines()) {
            Map<String, Object> r = new HashMap<>();
            r.put("NGAY", l.date() == null ? null : java.sql.Timestamp.valueOf(l.date()));
            r.put("NOI_DUNG", l.description());
            r.put("TANG", l.increase());
            r.put("GIAM", l.decrease());
            r.put("CON_NO", l.balance());
            r.put("DAM", l.highlight());
            rows.add(r);
        }
        return render("PhieuThuNo", p, rows);
    }
}
