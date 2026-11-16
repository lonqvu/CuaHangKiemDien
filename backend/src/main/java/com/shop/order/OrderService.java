package com.shop.order;

import com.shop.common.ApiException;
import com.shop.customer.Customer;
import com.shop.customer.CustomerRepository;
import com.shop.customer.DebtLedger;
import com.shop.customer.DebtService;
import com.shop.product.Product;
import com.shop.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final DebtService debts;

    public OrderService(OrderRepository orders, ProductRepository products,
                        CustomerRepository customers, DebtService debts) {
        this.orders = orders; this.products = products; this.customers = customers; this.debts = debts;
    }

    public record ItemRequest(Long productId, BigDecimal quantity, Integer sheets, BigDecimal factor, BigDecimal unitPrice) {}
    public record CheckoutRequest(Long customerId, String customerName, String customerAddress, String customerPhone,
                                  BigDecimal shippingFee, BigDecimal paidAmount, String note) {}

    private Order find(Long id) {
        return orders.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy hóa đơn"));
    }

    private Order findDraft(Long id) {
        Order o = find(id);
        if (o.getStatus() != Order.Status.DRAFT) throw ApiException.conflict("Hóa đơn đã chốt, không sửa được");
        return o;
    }

    @Transactional
    public Order createDraft(String staffName) {
        Order o = new Order();
        o.setStaffName(staffName); // chua co ma: ma HD chi cap khi chot (so hoa don lien tuc, nhap bo do khong lam nhay so)
        return orders.save(o);
    }

    @Transactional(readOnly = true)
    public Order get(Long id) {
        Order o = find(id);
        o.getItems().size(); // nap items trong transaction
        return o;
    }

    /** Bo loc danh sach hoa don. Ngay loc theo ngay chot (nhap: ngay tao), giong man Thong ke. */
    public record Filter(List<Order.Status> statuses, java.time.LocalDate from, java.time.LocalDate to, String q, Long customerId) {
        LocalDateTime fromTime() { return from == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : from.atStartOfDay(); }
        LocalDateTime toTime() { return to == null ? LocalDateTime.of(9999, 1, 1, 0, 0) : to.plusDays(1).atStartOfDay(); }
        long customer() { return customerId == null ? 0 : customerId; }
        String like() { return com.shop.common.PageDto.like(q); }
    }

    public record Summary(long count, BigDecimal itemsTotal, BigDecimal collected, BigDecimal debtAfter,
                          java.util.Map<String, Long> countByStatus) {}

    /** Mot trang hoa don (KHONG nap dong hang - chi tiet lay qua get(id)). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Order> page(Filter f, org.springframework.data.domain.Pageable p) {
        return orders.page(f.statuses(), f.fromTime(), f.toTime(), f.like(), f.customer(), p);
    }

    /** Tong tren toan bo ket qua loc + so hoa don moi trang thai (cho cac tab), cung dieu kien ngay/tim kiem/khach. */
    @Transactional(readOnly = true)
    public Summary summary(Filter f) {
        Object[] s = orders.summary(f.statuses(), f.fromTime(), f.toTime(), f.like(), f.customer()).get(0);
        java.util.Map<String, Long> byStatus = new java.util.LinkedHashMap<>();
        for (Order.Status st : Order.Status.values()) byStatus.put(st.name(), 0L);
        for (Object[] r : orders.countByStatus(f.fromTime(), f.toTime(), f.like(), f.customer()))
            byStatus.put(((Order.Status) r[0]).name(), (Long) r[1]);
        return new Summary((Long) s[0], (BigDecimal) s[1], (BigDecimal) s[2], (BigDecimal) s[3], byStatus);
    }

    @Transactional
    public Order addItem(Long orderId, ItemRequest r) {
        Order o = findDraft(orderId);
        OrderItem it = new OrderItem();
        it.setOrder(o);
        fill(it, r);
        o.getItems().add(it);
        recalc(o);
        return o;
    }

    @Transactional
    public Order updateItem(Long orderId, Long itemId, ItemRequest r) {
        Order o = findDraft(orderId);
        OrderItem it = o.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng hàng"));
        fill(it, r);
        recalc(o);
        return o;
    }

    @Transactional
    public Order removeItem(Long orderId, Long itemId) {
        Order o = findDraft(orderId);
        o.getItems().removeIf(i -> i.getId().equals(itemId));
        recalc(o);
        return o;
    }

    @Transactional
    public void deleteDraft(Long orderId) {
        orders.delete(findDraft(orderId));
    }

    private void fill(OrderItem it, ItemRequest r) {
        if (r.productId() == null) throw ApiException.badRequest("Chưa chọn sản phẩm");
        if (r.quantity() == null || r.quantity().signum() <= 0) throw ApiException.badRequest("Số lượng phải lớn hơn 0");
        Product p = products.findById(r.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> ApiException.badRequest("Sản phẩm không tồn tại hoặc đã ngừng bán"));
        int sheets = r.sheets() == null ? 0 : r.sheets();
        if (sheets < 0) throw ApiException.badRequest("Số tấm không hợp lệ");
        BigDecimal factor = LineCalculator.normalizeFactor(sheets, r.factor());
        BigDecimal price = r.unitPrice() != null ? r.unitPrice() : p.getPrice();
        if (price.signum() < 0) throw ApiException.badRequest("Đơn giá không hợp lệ");
        BigDecimal rawQty = LineCalculator.totalQuantity(r.quantity(), sheets, factor);

        it.setProductId(p.getId());
        it.setProductName(p.getName());
        it.setUnit(p.getUnit());
        it.setUnitPrice(price);
        it.setQuantity(r.quantity());
        it.setSheets(sheets);
        it.setFactor(factor);
        it.setTotalQuantity(LineCalculator.storedQuantity(rawQty));
        it.setLineTotal(LineCalculator.lineTotal(price, rawQty));
    }

    private void recalc(Order o) {
        o.setItemsTotal(o.getItems().stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        // o dang duoc quan ly trong transaction: chi flush (cascade PERSIST cho dong moi, co id tra ve client).
        // Khong dung saveAndFlush: no goi merge() -> Hibernate 6.5 bao TransientObjectException voi dong moi them.
        orders.flush();
    }

    /**
     * Chot hoa don. Toan bo (hoa don + cong no + so cai) trong 1 transaction: loi giua chung thi rollback het.
     */
    @Transactional
    public Order checkout(Long orderId, CheckoutRequest r) {
        Order o = findDraft(orderId);
        if (o.getItems().isEmpty()) throw ApiException.badRequest("Hóa đơn chưa có mặt hàng");

        BigDecimal ship = r.shippingFee() == null ? BigDecimal.ZERO : r.shippingFee();
        BigDecimal paid = r.paidAmount() == null ? BigDecimal.ZERO : r.paidAmount();
        if (ship.signum() < 0 || paid.signum() < 0) throw ApiException.badRequest("Số tiền không hợp lệ");

        BigDecimal itemsTotal = o.getItems().stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal charge = itemsTotal.add(ship);

        Customer c = null;
        if (r.customerId() != null) {
            c = customers.findById(r.customerId())
                    .filter(Customer::isActive)
                    .orElseThrow(() -> ApiException.badRequest("Khách hàng không tồn tại hoặc đã bị ẩn"));
        } else if (r.customerName() == null || r.customerName().isBlank()) {
            // Khach le (app cu): bat buoc co ten
            throw ApiException.badRequest("Nhập tên khách lẻ hoặc chọn khách hàng");
        }

        // Cap ma khi chot, sau khi da kiem tra xong: so hoa don lien tuc (chi hut so neu loi DB giua chung)
        if (o.getCode() == null) o.setCode(String.format("HD%08d", orders.nextCode()));
        o.setItemsTotal(itemsTotal);
        o.setShippingFee(ship);
        o.setNote(r.note());

        if (c != null) {
            BigDecimal previous = c.getDebt();
            BigDecimal payable = charge.add(previous);
            // Giong PayDialog cu: Con lai = max(0, Tong thanh toan - Khach tra); tra du -> con lai 0, luu nguyen so khach dua
            BigDecimal remain = payable.subtract(paid).max(BigDecimal.ZERO);

            o.setCustomerId(c.getId());
            o.setCustomerName(c.getName());
            o.setCustomerAddress(c.getAddress());
            o.setCustomerPhone(c.getPhone());
            o.setPreviousDebt(previous);
            o.setTotalPayable(payable);
            o.setPaidAmount(paid);
            o.setDebtAfter(remain);

            debts.apply(c, DebtLedger.Type.ORDER, charge, o.getId(), "Hóa đơn " + o.getCode());
            BigDecimal applied = paid.min(payable); // phan tien thua tra lai khach, khong tru vao no
            if (applied.signum() > 0) {
                debts.apply(c, DebtLedger.Type.PAYMENT, applied.negate(), o.getId(), "Thanh toán hóa đơn " + o.getCode());
            }
        } else {
            // Khach le: duoc tra thieu, phan con lai chi ghi tren hoa don, khong vao cong no
            o.setCustomerName(r.customerName().trim());
            o.setCustomerAddress(r.customerAddress());
            o.setCustomerPhone(r.customerPhone());
            o.setPreviousDebt(BigDecimal.ZERO);
            o.setTotalPayable(charge);
            o.setPaidAmount(paid);
            o.setDebtAfter(charge.subtract(paid).max(BigDecimal.ZERO));
        }

        o.setStatus(Order.Status.PAID);
        o.setCheckedOutAt(LocalDateTime.now());
        return o;
    }

    /** Huy hoa don da chot: giu lai hoa don (CANCELLED) va hoan lai cong no. Khac app cu (xoa cung, no bi lech). */
    @Transactional
    public Order cancel(Long orderId) {
        Order o = find(orderId);
        if (o.getStatus() != Order.Status.PAID) throw ApiException.conflict("Chỉ hủy được hóa đơn đã chốt");
        if (o.getCustomerId() != null) {
            Customer c = customers.findById(o.getCustomerId()).orElseThrow();
            BigDecimal netDebtAdded = o.getDebtAfter().subtract(o.getPreviousDebt()); // hoa don nay lam no thay doi bao nhieu
            debts.apply(c, DebtLedger.Type.ADJUST, netDebtAdded.negate(), o.getId(), "Hủy hóa đơn " + o.getCode());
        }
        o.setStatus(Order.Status.CANCELLED);
        o.getItems().size();
        return o;
    }
}
