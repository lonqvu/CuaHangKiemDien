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
                throw new IllegalStateException("Không nạp được mẫu in " + n, e);
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

    /** Bien lai thanh toan cong no (ThanhToanNoReport.jrxml). */
    public byte[] debtReceipt(long customerId, String name, String address, String phone,
                              java.math.BigDecimal debt, List<DebtReceiptData.Payment> payments, Double lastPayment) {
        Map<String, Object> p = new HashMap<>();
        p.put("CustomerID", String.valueOf(customerId));
        p.put("PaymentAmount", lastPayment);
        return render("ThanhToanNoReport", p, DebtReceiptData.rows(customerId, name, address, phone, debt, payments));
    }
}
