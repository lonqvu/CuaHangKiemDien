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
        if (o.getItems().isEmpty()) throw ApiException.badRequest("Hoa don chua co mat hang");
        BillReportData.Header h = new BillReportData.Header(o.getCode(), o.getCreatedAt(), o.getCustomerName(),
                o.getCustomerAddress(), o.getCustomerPhone(), o.getPreviousDebt(), o.getItemsTotal(), o.getTotalPayable(),
                o.getPaidAmount(), o.getDebtAfter(), o.getShippingFee(), o.getStaffName(), o.getNote());
        List<BillReportData.Line> lines = o.getItems().stream().map(i -> new BillReportData.Line(i.getId(), i.getProductId(),
                i.getProductName(), i.getUnit(), i.getQuantity(), i.getSheets(), i.getFactor(), i.getUnitPrice(),
                i.getTotalQuantity(), i.getLineTotal())).toList();
        return pdf(reports.invoice(h, lines), o.getCode() + ".pdf");
    }

    /** Phieu thu no (in ngay sau khi thu, in lai luc nao cung ra dung so lieu cu). */
    @GetMapping("/api/customers/debt-payments/{ledgerId}/receipt.pdf")
    public ResponseEntity<byte[]> debtReceipt(@PathVariable Long ledgerId) {
        DebtStatementService.Doc d = statements.receipt(ledgerId);
        return pdf(reports.debtDoc(d), "PhieuThu_" + d.docNo().replace("Số: ", "") + ".pdf");
    }

    /** Sao ke cong no theo khoang ngay. */
    @GetMapping("/api/customers/{id}/statement.pdf")
    public ResponseEntity<byte[]> statement(@PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        DebtStatementService.Doc d = statements.statement(id, from, to);
        return pdf(reports.debtDoc(d), "SaoKe_" + id + "_" + (from == null ? "dau" : from) + "_" + (to == null ? LocalDate.now() : to) + ".pdf");
    }

    private static ResponseEntity<byte[]> pdf(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
