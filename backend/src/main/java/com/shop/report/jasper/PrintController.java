package com.shop.report.jasper;

import com.shop.common.ApiException;
import com.shop.customer.DebtStatementService;
import com.shop.order.Order;
import com.shop.order.OrderService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** In PDF bang mau Jasper. Hoa don: HDxxxxxxxx.pdf (giong app cu); phieu thu: PhieuThu_PTxxxxxx.pdf; sao ke: SaoKe_{id}_{tu}_{den}.pdf */
@RestController
public class PrintController {
    private final OrderService orders;
    private final DebtStatementService statements;
    private final JasperReportService reports;

    public PrintController(OrderService orders, DebtStatementService statements, JasperReportService reports) {
        this.orders = orders; this.statements = statements; this.reports = reports;
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

    /** Phieu thu no (in ngay sau khi thu, in lai luc nao cung ra dung so lieu cu). */
    @GetMapping("/api/customers/debt-payments/{ledgerId}/receipt.pdf")
    public ResponseEntity<byte[]> debtReceipt(@PathVariable Long ledgerId) {
        DebtStatementService.Doc d = statements.receipt(ledgerId);
        return pdf(reports.debtDoc(d, JasperReportService.Format.PDF), receiptName(d) + ".pdf");
    }

    /** Anh phieu thu no (PNG) de gui Zalo - cung mau voi ban in. */
    @GetMapping("/api/customers/debt-payments/{ledgerId}/receipt.png")
    public ResponseEntity<byte[]> debtReceiptImage(@PathVariable Long ledgerId) {
        DebtStatementService.Doc d = statements.receipt(ledgerId);
        return png(reports.debtDoc(d, JasperReportService.Format.PNG), receiptName(d) + ".png");
    }

    /** Sao ke cong no theo khoang ngay. */
    @GetMapping("/api/customers/{id}/statement.pdf")
    public ResponseEntity<byte[]> statement(@PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return pdf(reports.debtDoc(statements.statement(id, from, to), JasperReportService.Format.PDF), statementName(id, from, to) + ".pdf");
    }

    /** Anh sao ke cong no (PNG) de gui Zalo khi khach dang no ma chua co phieu thu nao de gui. */
    @GetMapping("/api/customers/{id}/statement.png")
    public ResponseEntity<byte[]> statementImage(@PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return png(reports.debtDoc(statements.statement(id, from, to), JasperReportService.Format.PNG), statementName(id, from, to) + ".png");
    }

    private static String receiptName(DebtStatementService.Doc d) {
        return "PhieuThu_" + d.docNo().replace("Số: ", "");
    }

    private static String statementName(Long id, LocalDate from, LocalDate to) {
        return "SaoKe_" + id + "_" + (from == null ? "dau" : from) + "_" + (to == null ? LocalDate.now() : to);
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
