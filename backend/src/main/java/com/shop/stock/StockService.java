package com.shop.stock;

import com.shop.common.ApiException;
import com.shop.order.Order;
import com.shop.order.OrderItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Noi DUY NHAT thay doi ton kho. Moi thay doi = 1 dong so kho (StockMovement) + cap nhat so du (StockBalance) trong cung transaction
 * cua chung tu goi toi (chot hoa don, ghi so phieu nhap...): loi o dau la rollback ca chung tu lan kho.
 *
 * Gia von: binh quan gia quyen di dong (nhu KiotViet). Nhap co gia -> tinh lai binh quan; xuat -> theo binh quan hien tai, khong doi binh quan.
 * Ton dang <= 0 ma nhap vao -> binh quan = gia nhap (khong de phan am keo lech gia).
 * Ban khi chua tung co gia von (binh quan = 0) -> van xuat kho, gia von dong hang de trong (NULL) de bao cao lai gop biet la thieu.
 *
 * Chong ban trung: khoa dong so du (SELECT ... FOR UPDATE) theo thu tu productId tang dan -> khong deadlock khi nhieu quay cung chot.
 */
@Service
public class StockService {
    public static final String REF_ORDER = "ORDER", REF_RECEIPT = "RECEIPT", REF_COUNT = "COUNT", REF_ADJUST = "ADJUST";

    private final StockBalanceRepository balances;
    private final StockMovementRepository movements;
    /** total: tru kho theo Tong SL (SL x tam x he so) - mac dinh; without-factor: tru SL x so tam (he so chi de tinh gia). */
    private final String saleQtyMode;

    public StockService(StockBalanceRepository balances, StockMovementRepository movements,
                        @Value("${app.stock.sale-quantity:total}") String saleQtyMode) {
        this.balances = balances; this.movements = movements; this.saleQtyMode = saleQtyMode;
    }

    /** Mot lan thay doi ton. unitCost null = theo gia von binh quan hien tai. */
    public record Move(Long productId, BigDecimal qty, BigDecimal unitCost, StockMovement.Type type,
                       String refType, Long refId, Long refLineId, String refCode, String note, String by) {}

    /** Tao dong so du cho san pham moi (ton 0). */
    @Transactional
    public void ensureBalance(Long productId) {
        if (!balances.existsById(productId)) {
            StockBalance b = new StockBalance();
            b.setProductId(productId);
            balances.saveAndFlush(b);
        }
    }

    /** Khoa truoc cac dong so du theo thu tu tang dan (tranh deadlock giua 2 transaction khoa cheo nhau). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAll(Collection<Long> productIds) {
        new TreeSet<>(productIds).forEach(this::lockOrCreate);
    }

    private StockBalance lockOrCreate(Long productId) {
        return balances.lock(productId).orElseGet(() -> {
            ensureBalance(productId);
            return balances.lock(productId).orElseThrow();
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement apply(Move m) {
        if (m.qty() == null || m.qty().signum() == 0) throw ApiException.badRequest("Số lượng phải khác 0");
        StockBalance b = lockOrCreate(m.productId());
        BigDecimal onHand = b.getOnHand();
        BigDecimal avg = b.getAvgCost();
        BigDecimal q = m.qty().setScale(3, RoundingMode.HALF_UP);
        BigDecimal cost = (m.unitCost() != null ? m.unitCost() : avg).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        BigDecimal after = onHand.add(q);

        // Chi cac nghiep vu mang gia von rieng moi tinh lai binh quan
        boolean revalue = m.unitCost() != null && switch (m.type()) {
            case RECEIPT, RECEIPT_CANCEL, SALE_CANCEL -> true;
            case SALE_REVISE -> q.signum() > 0; // sua hoa don giam hang: nhap lai theo gia von da xuat
            case COUNT -> q.signum() > 0;
            default -> false;
        };
        BigDecimal newAvg = avg;
        if (revalue) {
            if (q.signum() > 0) {
                newAvg = onHand.signum() <= 0
                        ? cost
                        : onHand.multiply(avg).add(q.multiply(cost)).divide(after, 4, RoundingMode.HALF_UP);
            } else if (onHand.signum() > 0 && after.signum() > 0) {
                // Huy phieu nhap: rut dung gia tri da nhap ra khoi kho
                newAvg = onHand.multiply(avg).add(q.multiply(cost)).divide(after, 4, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
            }
        }

        b.setOnHand(after);
        b.setAvgCost(newAvg);

        StockMovement mv = new StockMovement();
        mv.setProductId(m.productId());
        mv.setType(m.type());
        mv.setQty(q);
        mv.setUnitCost(cost);
        mv.setValue(q.multiply(cost).setScale(2, RoundingMode.HALF_UP));
        mv.setBalanceAfter(after);
        mv.setAvgCostAfter(newAvg);
        mv.setRefType(m.refType());
        mv.setRefId(m.refId());
        mv.setRefLineId(m.refLineId());
        mv.setRefCode(m.refCode());
        mv.setNote(m.note());
        mv.setCreatedBy(m.by());
        return movements.save(mv);
    }

    /** So luong xuat kho cua 1 dong hoa don, theo cau hinh app.stock.sale-quantity. */
    public BigDecimal saleQty(OrderItem i) {
        if ("without-factor".equalsIgnoreCase(saleQtyMode))
            return i.getSheets() > 0 ? i.getQuantity().multiply(BigDecimal.valueOf(i.getSheets())) : i.getQuantity();
        return i.getTotalQuantity();
    }

    /** Chot hoa don: xuat kho tung dong, ghi gia von vao dong hang. Goi trong transaction chot hoa don. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void issueForOrder(Order o, String by) {
        lockAll(o.getItems().stream().map(OrderItem::getProductId).filter(Objects::nonNull).toList());
        for (OrderItem i : o.getItems()) {
            BigDecimal qty = saleQty(i);
            if (i.getProductId() == null || qty.signum() <= 0) continue;
            StockMovement mv = apply(new Move(i.getProductId(), qty.negate(), null, StockMovement.Type.SALE,
                    REF_ORDER, o.getId(), i.getId(), o.getCode(), "Bán hàng", by));
            i.setCostTotal(mv.getUnitCost().signum() > 0 ? mv.getValue().negate() : null);
        }
    }

    /**
     * Huy chung tu: ghi nguoc DUNG cac dong so kho da ghi (cung so luong, cung gia von).
     * Hoa don cu tu truoc khi co kho (khong co dong so kho) thi khong lam gi - khong "tra lai" hang chua tung tru.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverse(String refType, Long refId, StockMovement.Type original, StockMovement.Type reversal, String note, String by) {
        List<StockMovement> done = movements.findByRefTypeAndRefIdAndTypeOrderByIdAsc(refType, refId, original);
        lockAll(done.stream().map(StockMovement::getProductId).toList());
        for (StockMovement m : done) {
            apply(new Move(m.getProductId(), m.getQty().negate(), m.getUnitCost(), reversal,
                    refType, refId, m.getRefLineId(), m.getRefCode(), note, by));
        }
    }

    /** Da xuat (rong) cho 1 hoa don, theo san pham: {so luong, gia tri} (duong = da xuat). Gom ban, huy, sua. */
    public Map<Long, BigDecimal[]> issuedForOrder(Long orderId) {
        Map<Long, BigDecimal[]> m = new HashMap<>();
        for (StockMovement mv : movements.findByRefTypeAndRefIdOrderByIdAsc(REF_ORDER, orderId)) {
            BigDecimal[] a = m.computeIfAbsent(mv.getProductId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            a[0] = a[0].subtract(mv.getQty());
            a[1] = a[1].subtract(mv.getValue());
        }
        return m;
    }

    /** Hoa don co xuat kho luc chot khong (hoa don tu truoc khi co kho / du lieu phan mem cu thi khong). */
    public boolean wasIssued(Long orderId) {
        return !movements.findByRefTypeAndRefIdAndTypeOrderByIdAsc(REF_ORDER, orderId, StockMovement.Type.SALE).isEmpty();
    }

    /**
     * Sua hoa don: xuat them / nhap lai DUNG phan chenh lech so luong tung san pham (diff > 0: xuat them).
     * Nhap lai theo gia von binh quan da xuat cho hoa don nay.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void adjustOrder(Order o, Map<Long, BigDecimal> diff, String by) {
        Map<Long, BigDecimal[]> issued = issuedForOrder(o.getId());
        lockAll(diff.keySet());
        for (var e : new TreeMap<>(diff).entrySet()) {
            BigDecimal d = e.getValue().setScale(3, RoundingMode.HALF_UP);
            if (d.signum() == 0) continue;
            BigDecimal cost = null;
            BigDecimal[] a = issued.get(e.getKey());
            if (d.signum() < 0 && a != null && a[0].signum() > 0) cost = a[1].divide(a[0], 4, RoundingMode.HALF_UP);
            apply(new Move(e.getKey(), d.negate(), cost, StockMovement.Type.SALE_REVISE, REF_ORDER, o.getId(), null,
                    o.getCode(), "Sửa hóa đơn", by));
        }
    }

    /** Huy hoa don: nhap lai toan bo luong da xuat RONG (sau cac lan sua), theo gia von binh quan da xuat. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void returnOrder(Order o, String note, String by) {
        Map<Long, BigDecimal[]> issued = issuedForOrder(o.getId());
        lockAll(issued.keySet());
        for (var e : new TreeMap<>(issued).entrySet()) {
            BigDecimal q = e.getValue()[0];
            if (q.signum() <= 0) continue;
            BigDecimal cost = e.getValue()[1].divide(q, 4, RoundingMode.HALF_UP);
            apply(new Move(e.getKey(), q, cost, StockMovement.Type.SALE_CANCEL, REF_ORDER, o.getId(), null, o.getCode(), note, by));
        }
    }

    public Map<Long, StockBalance> balancesOf(Collection<Long> productIds) {
        Map<Long, StockBalance> map = new HashMap<>();
        if (!productIds.isEmpty()) balances.findByProductIdIn(productIds).forEach(b -> map.put(b.getProductId(), b));
        return map;
    }
}
