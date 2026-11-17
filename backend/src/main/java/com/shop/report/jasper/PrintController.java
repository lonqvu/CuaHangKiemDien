package com.shop.report.jasper;

import com.shop.common.ApiException;
import com.shop.customer.CustomerController;
import com.shop.order.Order;
import com.shop.order.OrderService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** In PDF bang mau Jasper cu. Ten file giong app cu: HDxxxxxxxx.pdf, ThanhToanNo_{id}_{yyyyMMdd_HHmmss}.pdf */
@RestController
public class PrintController {
    private final OrderService orders;
    private final CustomerController customers;
    private final JasperReportService reports;

    public PrintController(OrderService orders, CustomerController customers, JasperReportService reports) {
        this.orders = orders; this.customers = customers; this.reports = reports;
    }

    @GetMapping("/api/orders/{id}/invoice.pdf")
    public ResponseEntity<byte[]> invoice(@PathVariable Long id) {
        Order o = orders.get(id);
        return pdf(reports.invoice(header(o), lines(o), JasperReportService.Format.PDF), o.getCode() + ".pdf");
    }

    /** Anh hoa don (PNG) de gui Zalo - cung mau Bill.jrxml voi ban in. */
    @GetMapping("/api/orders/{id}/invoice.png")
    public ResponseEntity<byte[]> invoiceImage(@PathVariable Long id) {
        Order o = orders.get(id);
        return png(reports.invoice(header(o), lines(o), JasperReportService.Format.PNG), o.getCode() + ".png");
    }

    private static BillReportData.Header header(Order o) {
        if (o.getStatus() == Order.Status.DRAFT) throw ApiException.badRequest("Hóa đơn chưa chốt nên chưa in được");
        if (o.getItems().isEmpty()) throw ApiException.badRequest("Hóa đơn chưa có mặt hàng");
        return new BillReportData.Header(o.getCode(), o.getCreatedAt(), o.getCustomerName(),
                o.getCustomerAddress(), o.getCustomerPhone(), o.getPreviousDebt(), o.getItemsTotal(), o.getTotalPayable(),
                o.getPaidAmount(), o.getDebtAfter(), o.getShippingFee(), o.getStaffName(), o.getNote());
    }

    private static List<BillReportData.Line> lines(Order o) {
        return o.getItems().stream().map(i -> new BillReportData.Line(i.getId(), i.getProductId(),
                i.getProductName(), i.getUnit(), i.getQuantity(), i.getSheets(), i.getFactor(), i.getUnitPrice(),
                i.getTotalQuantity(), i.getLineTotal())).toList();
    }

    @GetMapping("/api/customers/{id}/payment-receipt.pdf")
    public ResponseEntity<byte[]> debtReceipt(@PathVariable Long id) {
        return pdf(receipt(id, JasperReportService.Format.PDF), receiptName(id) + ".pdf");
    }

    /** Anh bien lai cong no (PNG) de gui Zalo. */
    @GetMapping("/api/customers/{id}/payment-receipt.png")
    public ResponseEntity<byte[]> debtReceiptImage(@PathVariable Long id) {
        return png(receipt(id, JasperReportService.Format.PNG), receiptName(id) + ".png");
    }

    private byte[] receipt(Long id, JasperReportService.Format format) {
        CustomerController.Receipt r = customers.receipt(id);
        if (r.rows().isEmpty()) throw ApiException.badRequest("Khách chưa có lần trả nợ nào trong kỳ nợ hiện tại để in biên lai");
        List<DebtReceiptData.Payment> pays = r.rows().stream()
                .map(x -> new DebtReceiptData.Payment(x.date(), x.oldDebt(), x.amount(), x.remainingDebt())).toList();
        Double last = pays.get(pays.size() - 1).amount().doubleValue();
        return reports.debtReceipt(id, r.customer().getName(), r.customer().getAddress(), r.customer().getPhone(),
                r.currentDebt(), pays, last, format);
    }

    private static String receiptName(Long id) {
        return "ThanhToanNo_" + id + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    private static ResponseEntity<byte[]> png(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    private static ResponseEntity<byte[]> pdf(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
