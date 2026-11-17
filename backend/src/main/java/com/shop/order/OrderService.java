package com.shop.order;

import com.shop.common.ApiException;
import com.shop.customer.Customer;
import com.shop.customer.CustomerRepository;
import com.shop.customer.DebtLedger;
import com.shop.customer.DebtService;
import com.shop.product.Product;
import com.shop.product.ProductRepository;
import com.shop.stock.StockMovement;
import com.shop.stock.StockService;
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
    private final StockService stock;

    public OrderService(OrderRepository orders, ProductRepository products,
                        CustomerRepository customers, DebtService debts, StockService stock) {
        this.orders = orders; this.products = products; this.customers = customers; this.debts = debts; this.stock = stock;
    }

    public record ItemRequest(Long productId, BigDecimal quantity, Integer sheets, BigDecimal factor, BigDecimal unitPrice) {}
    /**
     * @param expectedPreviousDebt no cu ma man hinh dang hien. Neu may khac vua doi no cua khach (thu no, sua no...)
     *                             thi tu choi chot, de so tren hoa don / phieu thu dung voi so thu ngan da doc cho khach.
     *                             null = khong kiem tra (goi noi bo, test cu).
     */
    public record CheckoutRequest(Long customerId, String customerName, String customerAddress, String customerPhone,
                                  BigDecimal shippingFee, BigDecimal paidAmount, String note, BigDecimal expectedPreviousDebt) {
        public CheckoutRequest(Long customerId, String customerName, String customerAddress, String customerPhone,
                               BigDecimal shippingFee, BigDecimal paidAmount, String note) {
            this(customerId, customerName, customerAddress, customerPhone, shippingFee, paidAmount, note, null);
        }
    }

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
        // Cong thuc cu: so tam = 0 ma he so != 0 -> Tong SL = 0 -> dong hang 0 d. Gan nhu luon la go nham (xoa so tam
        // nhung he so 1,08 con lai) -> chan, de khong lot dong 0 d vao hoa don.
        if (LineCalculator.storedQuantity(rawQty).signum() == 0)
            throw ApiException.badRequest(sheets == 0 && factor.signum() != 0
                    ? "Tổng số lượng bằng 0: số tấm = 0 nhưng hệ số khác 0. Xóa hệ số hoặc nhập số tấm"
                    : "Tổng số lượng bằng 0");

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
        // o dang duoc quan ly trong transaction -> chi flush: cascade PERSIST luu dong moi va gan id cho client.
        // Khong goi save()/saveAndFlush(): voi entity da co id, Spring Data goi merge -> Hibernate tao ban sao cua
        // dong moi, con list items van giu ban goc chua luu -> TransientObjectException khi flush.
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
        } else if (paid.compareTo(charge) < 0) {
            // Khach le PHAI TRA DU (tien hang + ship). Khac app cu (cho tra thieu, phan thieu chi ghi tren hoa don,
            // khong ai theo doi de thu) -> muon ghi no thi chon / tao khach hang.
            throw ApiException.badRequest("Khách lẻ phải trả đủ " + charge.stripTrailingZeros().toPlainString()
                    + " (còn thiếu " + charge.subtract(paid).stripTrailingZeros().toPlainString()
                    + "). Muốn ghi nợ thì chọn hoặc tạo khách hàng");
        }
        // May khac vua doi no cua khach (thu no, sua no...) -> tu choi, de no cu tren hoa don dung so thu ngan dang thay
        if (c != null && r.expectedPreviousDebt() != null && r.expectedPreviousDebt().compareTo(c.getDebt()) != 0)
            throw ApiException.conflict("Nợ cũ của " + c.getName() + " vừa thay đổi: màn hình đang hiện "
                    + r.expectedPreviousDebt().stripTrailingZeros().toPlainString() + ", hiện tại là "
                    + c.getDebt().stripTrailingZeros().toPlainString() + ". Đã tải lại, kiểm tra rồi chốt lại.");

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
            // Khach le: PHAI TRA DU (da kiem tra o tren, truoc khi cap ma hoa don) -> con lai luon 0
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
        // Xuat kho + ghi gia von tung dong, cung transaction: loi kho thi khong chot duoc hoa don
        stock.issueForOrder(o, o.getStaffName());
        return o;
    }

    /**
     * Huy hoa don se lam gi voi cong no.
     * @param debtReversal so no duoc tru (am = no tang lai, khi hoa don nay da tru bot no cu)
     * @param refund       tien mat cua hang phai tra lai khach
     */
    public record CancelEffect(BigDecimal debtReversal, BigDecimal refund, BigDecimal debtBefore, BigDecimal debtAfter) {}

    /**
     * Hoa don nay lam no tang "net" = con no - no cu. Huy thi tru lai net, NHUNG no khong xuong duoi 0:
     * neu khach da tra bot/tra het (thu no sau do) thi chi tru phan con no, phan con lai cua hang tra lai tien mat.
     * Vd: mua chiu 570.000, tra het 570.000, roi huy -> no 0, tra lai khach 570.000.
     */
    @Transactional(readOnly = true)
    public CancelEffect cancelPreview(Long orderId) {
        Order o = find(orderId);
        if (o.getStatus() != Order.Status.PAID) throw ApiException.conflict("Chỉ hủy được hóa đơn đã chốt");
        if (o.getCustomerId() == null) return new CancelEffect(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        Customer c = customers.findById(o.getCustomerId()).orElseThrow();
        return effect(o, c);
    }

    private static CancelEffect effect(Order o, Customer c) {
        BigDecimal net = o.getDebtAfter().subtract(o.getPreviousDebt());
        BigDecimal reversal = net.signum() > 0 ? net.min(c.getDebt().max(BigDecimal.ZERO)) : net;
        BigDecimal refund = net.subtract(reversal).max(BigDecimal.ZERO);
        return new CancelEffect(reversal, refund, c.getDebt(), c.getDebt().subtract(reversal));
    }

    /** Huy hoa don da chot: giu lai hoa don (CANCELLED) va hoan lai cong no. Khac app cu (xoa cung, no bi lech). */
    @Transactional
    public Order cancel(Long orderId) {
        Order o = find(orderId);
        if (o.getStatus() != Order.Status.PAID) throw ApiException.conflict("Chỉ hủy được hóa đơn đã chốt");
        if (o.getCustomerId() != null) {
            Customer c = customers.findById(o.getCustomerId()).orElseThrow();
            CancelEffect e = effect(o, c);
            String note = "Hủy hóa đơn " + o.getCode()
                    + (e.refund().signum() > 0 ? "; trả lại khách " + e.refund().stripTrailingZeros().toPlainString() + " tiền mặt" : "");
            if (e.debtReversal().signum() != 0 || e.refund().signum() > 0)
                debts.apply(c, DebtLedger.Type.ADJUST, e.debtReversal().negate(), o.getId(), note);
            o.setRefundAmount(e.refund());
        }
        // Nhap tra dung so luong + gia von da xuat (hoa don tu truoc khi co kho thi khong co gi de tra)
        stock.reverse(StockService.REF_ORDER, o.getId(), StockMovement.Type.SALE, StockMovement.Type.SALE_CANCEL,
                "Hủy hóa đơn " + o.getCode(), null);
        o.setStatus(Order.Status.CANCELLED);
        o.getItems().size();
        return o;
    }
}
