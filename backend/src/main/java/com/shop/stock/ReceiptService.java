package com.shop.stock;

import com.shop.common.ApiException;
import com.shop.product.Product;
import com.shop.product.ProductRepository;
import com.shop.supplier.Supplier;
import com.shop.supplier.SupplierLedger;
import com.shop.supplier.SupplierService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Phieu nhap: nhap (DRAFT) -> ghi so (POSTED: cap ma PN, nhap kho, tinh lai gia von, ghi no NCC) -> huy (CANCELLED: ghi nguoc).
 * Ghi so / huy la 1 transaction: kho + cong no NCC + phieu cung thanh cong hoac cung rollback.
 */
@Service
public class ReceiptService {
    private final GoodsReceiptRepository receipts;
    private final ProductRepository products;
    private final ProductUnitRepository units;
    private final StockService stock;
    private final SupplierService suppliers;

    public ReceiptService(GoodsReceiptRepository receipts, ProductRepository products, ProductUnitRepository units,
                          StockService stock, SupplierService suppliers) {
        this.receipts = receipts; this.products = products; this.units = units; this.stock = stock; this.suppliers = suppliers;
    }

    /** inputUnit rong = don vi goc. qty: so luong theo don vi goc do thuc te (bo trong = inputQty x ty le quy doi). */
    public record LineRequest(Long productId, String inputUnit, BigDecimal inputQty, BigDecimal inputPrice, BigDecimal qty) {}
    public record ReceiptRequest(Long supplierId, String note, BigDecimal paidAmount, List<LineRequest> lines) {}

    public GoodsReceipt find(Long id) {
        return receipts.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu nhập"));
    }

    private GoodsReceipt findDraft(Long id) {
        GoodsReceipt r = find(id);
        if (r.getStatus() != GoodsReceipt.Status.DRAFT) throw ApiException.conflict("Phiếu đã ghi sổ, không sửa được. Hủy phiếu rồi lập phiếu mới.");
        return r;
    }

    @Transactional(readOnly = true)
    public GoodsReceipt get(Long id) {
        GoodsReceipt r = find(id);
        r.getLines().size();
        return r;
    }

    @Transactional
    public GoodsReceipt create(ReceiptRequest req, String by) {
        GoodsReceipt r = new GoodsReceipt();
        r.setCreatedBy(by);
        fill(r, req);
        return receipts.save(r);
    }

    @Transactional
    public GoodsReceipt update(Long id, ReceiptRequest req) {
        GoodsReceipt r = findDraft(id);
        fill(r, req);
        receipts.flush();
        return r;
    }

    @Transactional
    public void deleteDraft(Long id) { receipts.delete(findDraft(id)); }

    private void fill(GoodsReceipt r, ReceiptRequest req) {
        if (req.supplierId() != null) {
            Supplier s = suppliers.find(req.supplierId());
            if (!s.isActive()) throw ApiException.badRequest("Nhà cung cấp đã bị ẩn");
            r.setSupplierId(s.getId());
            r.setSupplierName(s.getName());
        } else {
            r.setSupplierId(null);
            r.setSupplierName(null);
        }
        r.setNote(req.note());
        BigDecimal paid = req.paidAmount() == null ? BigDecimal.ZERO : req.paidAmount();
        if (paid.signum() < 0) throw ApiException.badRequest("Số tiền đã trả không hợp lệ");
        r.setPaidAmount(paid);

        r.getLines().clear();
        receipts.flush(); // xoa dong cu truoc khi them dong moi
        if (req.lines() != null) for (LineRequest lr : req.lines()) r.getLines().add(line(r, lr));
        r.setTotal(r.getLines().stream().map(GoodsReceiptLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private GoodsReceiptLine line(GoodsReceipt r, LineRequest lr) {
        if (lr.productId() == null) throw ApiException.badRequest("Có dòng chưa chọn sản phẩm");
        Product p = products.findById(lr.productId()).orElseThrow(() -> ApiException.badRequest("Sản phẩm không tồn tại"));
        if (lr.inputQty() == null || lr.inputQty().signum() <= 0) throw ApiException.badRequest("Số lượng nhập của \"" + p.getName() + "\" phải lớn hơn 0");
        BigDecimal price = lr.inputPrice() == null ? BigDecimal.ZERO : lr.inputPrice();
        if (price.signum() < 0) throw ApiException.badRequest("Giá nhập của \"" + p.getName() + "\" không hợp lệ");

        String inUnit = lr.inputUnit() == null || lr.inputUnit().isBlank() ? p.getUnit() : lr.inputUnit().trim();
        BigDecimal ratio = BigDecimal.ONE;
        if (!Objects.equals(inUnit, p.getUnit())) {
            ratio = units.findByProductIdOrderByUnit(p.getId()).stream().filter(u -> u.getUnit().equalsIgnoreCase(inUnit))
                    .findFirst().map(ProductUnit::getRatio)
                    .orElseThrow(() -> ApiException.badRequest("Sản phẩm \"" + p.getName() + "\" chưa khai báo đơn vị quy đổi \"" + inUnit + "\""));
        }
        BigDecimal qty = lr.qty() != null && lr.qty().signum() > 0
                ? lr.qty().setScale(3, RoundingMode.HALF_UP)
                : lr.inputQty().multiply(ratio).setScale(3, RoundingMode.HALF_UP);
        if (qty.signum() <= 0) throw ApiException.badRequest("Số lượng quy đổi của \"" + p.getName() + "\" bằng 0");
        BigDecimal lineTotal = lr.inputQty().multiply(price).setScale(0, RoundingMode.HALF_UP);

        GoodsReceiptLine l = new GoodsReceiptLine();
        l.setReceipt(r);
        l.setProductId(p.getId());
        l.setProductName(p.getName());
        l.setUnit(p.getUnit());
        l.setInputUnit(inUnit);
        l.setInputQty(lr.inputQty());
        l.setInputPrice(price);
        l.setRatio(ratio);
        l.setQty(qty);
        l.setLineTotal(lineTotal);
        l.setUnitCost(lineTotal.divide(qty, 4, RoundingMode.HALF_UP));
        return l;
    }

    @Transactional
    public GoodsReceipt post(Long id, String by) {
        GoodsReceipt r = findDraft(id);
        if (r.getLines().isEmpty()) throw ApiException.badRequest("Phiếu nhập chưa có mặt hàng");
        BigDecimal applied = r.getPaidAmount().min(r.getTotal());
        if (r.getSupplierId() == null && applied.compareTo(r.getTotal()) < 0)
            throw ApiException.badRequest("Chọn nhà cung cấp để ghi nợ, hoặc nhập số đã trả bằng tổng tiền");

        r.setCode(String.format("PN%06d", receipts.nextCode()));
        stock.lockAll(r.getLines().stream().map(GoodsReceiptLine::getProductId).toList());
        for (GoodsReceiptLine l : r.getLines()) {
            stock.apply(new StockService.Move(l.getProductId(), l.getQty(), l.getUnitCost(), StockMovement.Type.RECEIPT,
                    StockService.REF_RECEIPT, r.getId(), l.getId(), r.getCode(), "Nhập hàng", by));
        }
        if (r.getSupplierId() != null) {
            Supplier s = suppliers.find(r.getSupplierId());
            suppliers.apply(s, SupplierLedger.Type.RECEIPT, r.getTotal(), r.getId(), "Phiếu nhập " + r.getCode());
            if (applied.signum() > 0)
                suppliers.apply(s, SupplierLedger.Type.PAYMENT, applied.negate(), r.getId(), "Trả tiền phiếu nhập " + r.getCode());
        }
        r.setStatus(GoodsReceipt.Status.POSTED);
        r.setPostedAt(LocalDateTime.now());
        return r;
    }

    @Transactional
    public GoodsReceipt cancel(Long id, String by) {
        GoodsReceipt r = find(id);
        if (r.getStatus() != GoodsReceipt.Status.POSTED) throw ApiException.conflict("Chỉ hủy được phiếu đã ghi sổ");
        stock.reverse(StockService.REF_RECEIPT, r.getId(), StockMovement.Type.RECEIPT, StockMovement.Type.RECEIPT_CANCEL,
                "Hủy phiếu nhập " + r.getCode(), by);
        if (r.getSupplierId() != null) {
            BigDecimal netDebt = r.getTotal().subtract(r.getPaidAmount().min(r.getTotal()));
            suppliers.apply(suppliers.find(r.getSupplierId()), SupplierLedger.Type.CANCEL, netDebt.negate(), r.getId(),
                    "Hủy phiếu nhập " + r.getCode());
        }
        r.setStatus(GoodsReceipt.Status.CANCELLED);
        r.setCancelledAt(LocalDateTime.now());
        r.getLines().size();
        return r;
    }
}
