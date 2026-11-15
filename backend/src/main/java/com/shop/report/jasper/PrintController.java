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
        if (o.getItems().isEmpty()) throw ApiException.badRequest("Hoa don chua co mat hang");
        BillReportData.Header h = new BillReportData.Header(o.getCode(), o.getCreatedAt(), o.getCustomerName(),
                o.getCustomerAddress(), o.getCustomerPhone(), o.getPreviousDebt(), o.getItemsTotal(), o.getTotalPayable(),
                o.getPaidAmount(), o.getDebtAfter(), o.getShippingFee(), o.getStaffName(), o.getNote());
        List<BillReportData.Line> lines = o.getItems().stream().map(i -> new BillReportData.Line(i.getId(), i.getProductId(),
                i.getProductName(), i.getUnit(), i.getQuantity(), i.getSheets(), i.getFactor(), i.getUnitPrice(),
                i.getTotalQuantity(), i.getLineTotal())).toList();
        return pdf(reports.invoice(h, lines), o.getCode() + ".pdf");
    }

    @GetMapping("/api/customers/{id}/payment-receipt.pdf")
    public ResponseEntity<byte[]> debtReceipt(@PathVariable Long id) {
        CustomerController.Receipt r = customers.receipt(id);
        if (r.rows().isEmpty()) throw ApiException.badRequest("Khong co ban ghi thanh toan nao de xuat hoa don!");
        List<DebtReceiptData.Payment> pays = r.rows().stream()
                .map(x -> new DebtReceiptData.Payment(x.date(), x.oldDebt(), x.amount(), x.remainingDebt())).toList();
        Double last = pays.get(pays.size() - 1).amount().doubleValue();
        byte[] pdf = reports.debtReceipt(id, r.customer().getName(), r.customer().getAddress(), r.customer().getPhone(),
                r.currentDebt(), pays, last);
        return pdf(pdf, "ThanhToanNo_" + id + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf");
    }

    private static ResponseEntity<byte[]> pdf(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
