package com.shop.report.jasper;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sinh PDF (in) hoac PNG (gui Zalo) bang chinh cac mau JasperReports cua app cu (src/main/resources/reports/*.jrxml).
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

    /** PDF de in; PNG de gui qua Zalo (anh hien ngay trong khung chat, khong can mo file). */
    public enum Format { PDF, PNG }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private byte[] render(String name, Map<String, Object> params, List<Map<String, ?>> rows, Format format) {
        params.put(JRParameter.REPORT_LOCALE, locale);
        try {
            JasperPrint print = JasperFillManager.fillReport(template(name), params, new JRMapCollectionDataSource((Collection) rows));
            return format == Format.PDF ? JasperExportManager.exportReportToPdf(print) : png(print);
        } catch (JRException | IOException e) {
            throw new IllegalStateException("Loi khi in " + name, e);
        }
    }

    /** Ve tung trang (zoom 2 cho net tren dien thoai), cat phan trang thua cuoi trang, ghep doc thanh 1 anh. */
    private static byte[] png(JasperPrint print) throws JRException, IOException {
        float zoom = 2f;
        List<BufferedImage> pages = new ArrayList<>();
        for (int i = 0; i < print.getPages().size(); i++) {
            BufferedImage img = (BufferedImage) JasperPrintManager.printPageToImage(print, i, zoom);
            // Jasper ve 1 duong vien 1-2px o mep phai/duoi trang: cat bo de anh gon
            pages.add(img.getSubimage(0, 0, img.getWidth() - EDGE / 3, contentHeight(img, Math.round(24 * zoom))));
        }
        int w = pages.stream().mapToInt(BufferedImage::getWidth).max().orElse(1);
        int h = Math.max(1, pages.stream().mapToInt(BufferedImage::getHeight).sum());
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        int y = 0;
        for (BufferedImage p : pages) { g.drawImage(p, 0, y, null); y += p.getHeight(); }
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bos);
        return bos.toByteArray();
    }

    /** Bo qua EDGE px sat mep khi do noi dung (vien trang cua Jasper). */
    private static final int EDGE = 6;

    /** Chieu cao toi dong cuoi co noi dung (khong trang) + le duoi. */
    private static int contentHeight(BufferedImage img, int margin) {
        for (int y = img.getHeight() - 1 - EDGE; y >= 0; y--) {
            for (int x = EDGE; x < img.getWidth() - EDGE; x += 2) {
                int rgb = img.getRGB(x, y);
                if (((rgb >> 16) & 0xff) < 245 || ((rgb >> 8) & 0xff) < 245 || (rgb & 0xff) < 245)
                    return Math.min(img.getHeight(), y + 1 + margin);
            }
        }
        return img.getHeight();
    }

    /** Hoa don ban hang (Bill.jrxml). */
    public byte[] invoice(BillReportData.Header header, List<BillReportData.Line> lines, Format format) {
        Map<String, Object> p = new HashMap<>();
        p.put("ORDER_ID", header.orderCode());
        p.put("LOGO", image("logo.jpg"));
        p.put("QR", image("qr.jpg"));
        return render("Bill", p, BillReportData.rows(header, lines), format);
    }

    /**
     * Phieu thu no / sao ke cong no (PhieuThuNo.jrxml). Dau trang giong mau bien lai cu; bang 5 cot tu so cong no.
     * PDF de in, PNG de gui Zalo (cung mau). Toi da 4 dong tong ket, dong cuoi in dam (Con no / So du cuoi ky) -> dien tu duoi len.
     */
    public byte[] debtDoc(com.shop.customer.DebtStatementService.Doc d, Format format) {
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
        return render("PhieuThuNo", p, rows, format);
    }
}
