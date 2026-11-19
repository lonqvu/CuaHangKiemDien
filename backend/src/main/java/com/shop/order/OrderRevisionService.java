package com.shop.order;

import com.shop.common.ApiException;
import com.shop.common.CurrentUser;
import com.shop.customer.Customer;
import com.shop.customer.CustomerRepository;
import com.shop.customer.DebtLedger;
import com.shop.customer.DebtService;
import com.shop.product.Product;
import com.shop.product.ProductRepository;
import com.shop.stock.StockService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Sua hoa don DA CHOT (chi quan tri, khong gioi han thoi gian). Giu nguyen ma hoa don, tinh lai het trong 1 transaction:
 *  - Dong hang (sua / xoa / them), phi ship, khach tra, ghi chu -> tien hang, tong thanh toan, con no cua hoa don.
 *    No cu tren hoa don (no truoc khi mua) giu nguyen.
 *  - Cong no khach: chenh lech "con no" cua hoa don (moi - cu) ghi vao so no (dong "Sua HD..."). Giam nhieu hon so
 *    khach dang no (khach da tra sau do) -> no ve 0, phan con lai cua hang tra lai khach tien mat (giong huy hoa don).
 *  - Khach le: phai tra du nhu luc chot, khong co no.
 *  - Kho: xuat them / nhap lai dung phan chenh lech so luong tung san pham.
 *  - Lich su: moi lan sua luu ai, luc nao, ly do, truoc / sau (order_revision).
 * App cu cho sua dong hang sau khi thanh toan nhung KHONG tinh lai tong tien / cong no -> 32 hoa don lech trong du lieu cu.
 */
@Service
public class OrderRevisionService {
    private final OrderRepository orders;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final DebtService debts;
    private final StockService stock;
    private final JdbcTemplate jdbc;

    public OrderRevisionService(OrderRepository orders, ProductRepository products, CustomerRepository customers,
                                DebtService debts, StockService stock, JdbcTemplate jdbc) {
        this.orders = orders; this.products = products; this.customers = customers; this.debts = debts; this.stock = stock; this.jdbc = jdbc;
    }

    /** id: dong co san (sua); id trong: dong moi (bat buoc productId). productId khac dong cu: doi san pham. */
    public record ReviseItem(Long id, Long productId, BigDecimal quantity, Integer sheets, BigDecimal factor, BigDecimal unitPrice) {}

    public record ReviseRequest(List<ReviseItem> items, BigDecimal shippingFee, BigDecimal paidAmount, String note, String reason) {}

    public record Line(Long id, Long productId, String productName, String unit, BigDecimal unitPrice, BigDecimal quantity,
                       int sheets, BigDecimal factor, BigDecimal totalQuantity, BigDecimal lineTotal) {}

    /**
     * Ket qua sua (xem truoc hoac da luu).
     * debtChange: so no cua khach thay doi bao nhieu (am = giam); refund: cua hang tra lai khach tien mat.
     */
    public record Effect(List<Line> lines, BigDecimal itemsTotalBefore, BigDecimal itemsTotalAfter, BigDecimal shippingFee,
                         BigDecimal previousDebt, BigDecimal totalPayableBefore, BigDecimal totalPayableAfter,
                         BigDecimal paidBefore, BigDecimal paidAfter, BigDecimal debtAfterBefore, BigDecimal debtAfterAfter,
                         BigDecimal customerDebtBefore, BigDecimal customerDebtAfter, BigDecimal debtChange, BigDecimal refund) {}

    public record Revision(Long id, LocalDateTime revisedAt, String revisedBy, String reason, BigDecimal debtDelta,
                           BigDecimal refund, String before, String after) {}

    @Transactional(readOnly = true)
    public Effect preview(Long orderId, ReviseRequest r) {
        Order o = findPaid(orderId);
        return plan(o, r, o.getCustomerId() == null ? null : customers.findById(o.getCustomerId()).orElse(null)).effect();
    }

    @Transactional
    public Order revise(Long orderId, ReviseRequest r) {
        Order o = findPaid(orderId);
        Customer c = o.getCustomerId() == null ? null : customers.findById(o.getCustomerId())
                .orElseThrow(() -> ApiException.badRequest("Không tìm thấy khách của hóa đơn này"));
        Plan p = plan(o, r, c);
        Effect e = p.effect();
        String before = describe(o);
        String by = CurrentUser.name();

        // ---- Kho: chenh lech so luong xuat theo san pham (tinh tren dong cu / moi, cach tinh xuat kho hien tai)
        Map<Long, BigDecimal> diff = new HashMap<>();
        for (OrderItem i : o.getItems()) if (i.getProductId() != null) diff.merge(i.getProductId(), stock.saleQty(i).negate(), BigDecimal::add);
        boolean tracked = stock.wasIssued(o.getId());

        // ---- Dong hang: sua tai cho dong giu lai (giu id), xoa dong bi bo, them dong moi
        Map<Long, OrderItem> byId = new HashMap<>();
        o.getItems().forEach(i -> byId.put(i.getId(), i));
        Set<Long> kept = new HashSet<>();
        List<OrderItem> next = new ArrayList<>();
        for (Line l : p.lines()) {
            OrderItem it = l.id() != null ? byId.get(l.id()) : new OrderItem();
            if (l.id() == null) { it.setOrder(o); } else kept.add(l.id());
            it.setProductId(l.productId());
            it.setProductName(l.productName());
            it.setUnit(l.unit());
            it.setUnitPrice(l.unitPrice());
            it.setQuantity(l.quantity());
            it.setSheets(l.sheets());
            it.setFactor(l.factor());
            it.setTotalQuantity(l.totalQuantity());
            it.setLineTotal(l.lineTotal());
            next.add(it);
        }
        o.getItems().removeIf(i -> !kept.contains(i.getId()));
        for (OrderItem it : next) if (it.getId() == null) o.getItems().add(it);
        // giu thu tu nhu nguoi dung sap
        o.getItems().sort(Comparator.comparingInt(next::indexOf));

        o.setItemsTotal(e.itemsTotalAfter());
        o.setShippingFee(e.shippingFee());
        o.setTotalPayable(e.totalPayableAfter());
        o.setPaidAmount(e.paidAfter());
        o.setDebtAfter(e.debtAfterAfter());
        o.setNote(blankToNull(r.note()));
        orders.flush(); // dong moi co id truoc khi ghi so kho

        for (OrderItem i : o.getItems()) if (i.getProductId() != null) diff.merge(i.getProductId(), stock.saleQty(i), BigDecimal::add);
        diff.values().removeIf(v -> v.signum() == 0);
        if (!diff.isEmpty()) stock.adjustOrder(o, diff, by);
        // gia von tung dong (bao cao lai): theo binh quan da xuat cho hoa don; hoa don chua tung xuat kho -> khong ro
        Map<Long, BigDecimal[]> issued = tracked ? stock.issuedForOrder(o.getId()) : Map.of();
        for (OrderItem i : o.getItems()) {
            BigDecimal[] a = i.getProductId() == null ? null : issued.get(i.getProductId());
            i.setCostTotal(a != null && a[0].signum() > 0 && a[1].signum() > 0
                    ? stock.saleQty(i).multiply(a[1]).divide(a[0], 2, RoundingMode.HALF_UP) : null);
        }

        // ---- Cong no
        if (c != null && (e.debtChange().signum() != 0 || e.refund().signum() > 0)) {
            String note = "Sửa hóa đơn " + o.getCode() + ": còn nợ " + vnd(e.debtAfterBefore()) + " → " + vnd(e.debtAfterAfter())
                    + (e.refund().signum() > 0 ? "; trả lại khách " + vnd(e.refund()) + " tiền mặt" : "");
            debts.apply(c, DebtLedger.Type.REVISE, e.debtChange(), o.getId(), note);
        }
        if (e.refund().signum() > 0) o.setRefundAmount(o.getRefundAmount().add(e.refund()));

        jdbc.update("INSERT INTO order_revision (order_id, revised_by, reason, debt_delta, refund, before_text, after_text) VALUES (?, ?, ?, ?, ?, ?, ?)",
                o.getId(), by, blankToNull(r.reason()), e.debtChange(), e.refund(), before, describe(o));
        orders.flush();
        return o;
    }

    @Transactional(readOnly = true)
    public List<Revision> history(Long orderId) {
        return jdbc.query("SELECT id, revised_at, revised_by, reason, debt_delta, refund, before_text, after_text FROM order_revision "
                        + "WHERE order_id = ? ORDER BY id DESC",
                (rs, n) -> new Revision(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime(), rs.getString(3), rs.getString(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getString(7), rs.getString(8)), orderId);
    }

    // ------------------------------------------------------------------------------------------------------------

    private record Plan(List<Line> lines, Effect effect) {}

    private Plan plan(Order o, ReviseRequest r, Customer c) {
        if (r == null || r.items() == null || r.items().isEmpty()) throw ApiException.badRequest("Hóa đơn phải còn ít nhất 1 mặt hàng. Muốn bỏ cả hóa đơn thì Hủy hóa đơn");
        if (r.items().size() > 200) throw ApiException.badRequest("Tối đa 200 dòng");
        Map<Long, OrderItem> byId = new HashMap<>();
        o.getItems().forEach(i -> byId.put(i.getId(), i));
        Set<Long> seen = new HashSet<>();
        List<Line> lines = new ArrayList<>();
        int n = 0;
        for (ReviseItem it : r.items()) {
            n++;
            String w = "Dòng " + n + ": ";
            OrderItem old = null;
            if (it.id() != null) {
                old = byId.get(it.id());
                if (old == null) throw ApiException.badRequest(w + "không thuộc hóa đơn này");
                if (!seen.add(it.id())) throw ApiException.badRequest(w + "bị lặp");
            }
            Long pid; String name; String unit;
            boolean sameProduct = old != null && (it.productId() == null || Objects.equals(it.productId(), old.getProductId()));
            if (sameProduct) { // giu ten / don vi da in (ke ca san pham da ngung ban / da xoa)
                pid = old.getProductId(); name = old.getProductName(); unit = old.getUnit();
            } else {
                if (it.productId() == null) throw ApiException.badRequest(w + "chưa chọn sản phẩm");
                Product p = products.findById(it.productId()).filter(Product::isActive)
                        .orElseThrow(() -> ApiException.badRequest(w + "sản phẩm không tồn tại hoặc đã ngừng bán"));
                pid = p.getId(); name = p.getName(); unit = p.getUnit();
            }
            if (it.quantity() == null || it.quantity().signum() <= 0) throw ApiException.badRequest(w + "số lượng phải lớn hơn 0");
            int sheets = it.sheets() == null ? 0 : it.sheets();
            if (sheets < 0) throw ApiException.badRequest(w + "số tấm không hợp lệ");
            BigDecimal factor = LineCalculator.normalizeFactor(sheets, it.factor());
            BigDecimal price = it.unitPrice() != null ? it.unitPrice() : old != null && sameProduct ? old.getUnitPrice()
                    : products.findById(pid).map(Product::getPrice).orElse(null);
            if (price == null || price.signum() < 0) throw ApiException.badRequest(w + "đơn giá không hợp lệ");
            BigDecimal raw = LineCalculator.totalQuantity(it.quantity(), sheets, factor);
            if (LineCalculator.storedQuantity(raw).signum() == 0)
                throw ApiException.badRequest(w + (sheets == 0 && factor.signum() != 0
                        ? "tổng số lượng bằng 0: số tấm = 0 nhưng hệ số khác 0. Xóa hệ số hoặc nhập số tấm" : "tổng số lượng bằng 0"));
            lines.add(new Line(it.id(), pid, name, unit, price, it.quantity(), sheets, factor,
                    LineCalculator.storedQuantity(raw), LineCalculator.lineTotal(price, raw)));
        }

        BigDecimal ship = r.shippingFee() == null ? BigDecimal.ZERO : r.shippingFee();
        BigDecimal paid = r.paidAmount() == null ? BigDecimal.ZERO : r.paidAmount();
        if (ship.signum() < 0 || paid.signum() < 0) throw ApiException.badRequest("Số tiền không hợp lệ");
        BigDecimal itemsTotal = lines.stream().map(Line::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal charge = itemsTotal.add(ship);

        BigDecimal prev, payable, after;
        if (o.getCustomerId() != null) {
            prev = o.getPreviousDebt();
            payable = charge.add(prev);
            after = payable.subtract(paid).max(BigDecimal.ZERO); // nhu luc chot: tra du -> con lai 0
        } else {
            if (paid.compareTo(charge) < 0)
                throw ApiException.badRequest("Khách lẻ phải trả đủ " + vnd(charge) + " (còn thiếu " + vnd(charge.subtract(paid)) + ")");
            prev = BigDecimal.ZERO;
            payable = charge;
            after = BigDecimal.ZERO;
        }

        // Cong no khach: hoa don nay lam no thay doi (con no moi - con no cu); khong de no am
        BigDecimal change = after.subtract(o.getDebtAfter()), refund = BigDecimal.ZERO;
        BigDecimal custBefore = c == null ? null : c.getDebt(), custAfter = custBefore;
        if (c != null) {
            if (change.signum() < 0 && change.negate().compareTo(c.getDebt().max(BigDecimal.ZERO)) > 0) {
                refund = change.negate().subtract(c.getDebt().max(BigDecimal.ZERO));
                change = c.getDebt().max(BigDecimal.ZERO).negate();
            }
            custAfter = custBefore.add(change);
        } else change = BigDecimal.ZERO;

        return new Plan(lines, new Effect(lines, o.getItemsTotal(), itemsTotal, ship, prev, o.getTotalPayable(), payable,
                o.getPaidAmount(), paid, o.getDebtAfter(), after, custBefore, custAfter, change, refund));
    }

    private Order findPaid(Long id) {
        Order o = orders.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy hóa đơn"));
        if (o.getStatus() != Order.Status.PAID) throw ApiException.conflict("Chỉ sửa được hóa đơn đã chốt (hóa đơn đã hủy thì không sửa)");
        return o;
    }

    private static String describe(Order o) {
        StringBuilder b = new StringBuilder();
        for (OrderItem i : o.getItems())
            b.append("- ").append(i.getProductName()).append(": ").append(qty(i.getTotalQuantity()))
                    .append(i.getUnit() == null ? "" : " " + i.getUnit()).append(" × ").append(vnd(i.getUnitPrice()))
                    .append(" = ").append(vnd(i.getLineTotal())).append('\n');
        b.append("Tiền hàng ").append(vnd(o.getItemsTotal())).append(", ship ").append(vnd(o.getShippingFee()))
                .append(", tổng thanh toán ").append(vnd(o.getTotalPayable())).append(", khách trả ").append(vnd(o.getPaidAmount()))
                .append(", còn nợ ").append(vnd(o.getDebtAfter()));
        if (o.getNote() != null) b.append("\nGhi chú: ").append(o.getNote());
        return b.toString();
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static final DecimalFormatSymbols VN = DecimalFormatSymbols.getInstance(new Locale("vi", "VN"));

    static String vnd(BigDecimal v) { return new DecimalFormat("#,##0.##", VN).format(v == null ? BigDecimal.ZERO : v); }

    private static String qty(BigDecimal v) { return new DecimalFormat("#,##0.###", VN).format(v == null ? BigDecimal.ZERO : v); }
}
