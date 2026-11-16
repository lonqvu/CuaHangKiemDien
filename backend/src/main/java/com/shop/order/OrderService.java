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
        return orders.findById(id).orElseThrow(() -> ApiException.notFound("Khong tim thay hoa don"));
    }

    private Order findDraft(Long id) {
        Order o = find(id);
        if (o.getStatus() != Order.Status.DRAFT) throw ApiException.conflict("Hoa don da chot, khong the sua");
        return o;
    }

    @Transactional
    public Order createDraft(String staffName) {
        Order o = new Order();
        o.setCode(String.format("HD%08d", orders.nextCode()));
        o.setStaffName(staffName);
        return orders.save(o);
    }

    @Transactional(readOnly = true)
    public Order get(Long id) {
        Order o = find(id);
        o.getItems().size(); // nap items trong transaction
        return o;
    }

    @Transactional(readOnly = true)
    public List<Order> list(Order.Status status, java.time.LocalDate from, java.time.LocalDate to) {
        // App cu (OrderHistory) hien tat ca hoa don da thanh toan, moi nhat truoc
        List<Order> list = orders.search(status,
                from == null ? java.time.LocalDateTime.of(2000, 1, 1, 0, 0) : from.atStartOfDay(),
                to == null ? java.time.LocalDateTime.of(9999, 1, 1, 0, 0) : to.plusDays(1).atStartOfDay(),
                org.springframework.data.domain.PageRequest.of(0, 5000));
        list.forEach(o -> o.getItems().size()); // nap items trong transaction
        return list;
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
                .orElseThrow(() -> ApiException.notFound("Khong tim thay dong hang"));
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
        if (r.productId() == null) throw ApiException.badRequest("Chua chon san pham");
        if (r.quantity() == null || r.quantity().signum() <= 0) throw ApiException.badRequest("So luong phai lon hon 0");
        Product p = products.findById(r.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> ApiException.badRequest("San pham khong ton tai"));
        int sheets = r.sheets() == null ? 0 : r.sheets();
        if (sheets < 0) throw ApiException.badRequest("So tam khong hop le");
        BigDecimal factor = LineCalculator.normalizeFactor(sheets, r.factor());
        BigDecimal price = r.unitPrice() != null ? r.unitPrice() : p.getPrice();
        if (price.signum() < 0) throw ApiException.badRequest("Don gia khong hop le");
        BigDecimal rawQty = LineCalculator.totalQuantity(r.quantity(), sheets, factor);
        // Cong thuc cu: so tam = 0 ma he so != 0 -> Tong SL = 0 -> dong hang 0 d. Gan nhu luon la go nham (xoa so tam
        // nhung he so 1,08 con lai) -> chan, de khong lot dong 0 d vao hoa don.
        if (LineCalculator.storedQuantity(rawQty).signum() == 0)
            throw ApiException.badRequest(sheets == 0 && factor.signum() != 0
                    ? "Tong so luong bang 0: so tam = 0 nhung he so khac 0. Xoa he so hoac nhap so tam"
                    : "Tong so luong bang 0");

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
        if (o.getItems().isEmpty()) throw ApiException.badRequest("Hoa don chua co mat hang");

        BigDecimal ship = r.shippingFee() == null ? BigDecimal.ZERO : r.shippingFee();
        BigDecimal paid = r.paidAmount() == null ? BigDecimal.ZERO : r.paidAmount();
        if (ship.signum() < 0 || paid.signum() < 0) throw ApiException.badRequest("So tien khong hop le");

        BigDecimal itemsTotal = o.getItems().stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal charge = itemsTotal.add(ship);

        o.setItemsTotal(itemsTotal);
        o.setShippingFee(ship);
        o.setNote(r.note());

        if (r.customerId() != null) {
            Customer c = customers.findById(r.customerId())
                    .filter(Customer::isActive)
                    .orElseThrow(() -> ApiException.badRequest("Khach hang khong ton tai"));
            BigDecimal previous = c.getDebt();
            if (r.expectedPreviousDebt() != null && r.expectedPreviousDebt().compareTo(previous) != 0)
                throw ApiException.conflict("No cu cua " + c.getName() + " vua thay doi: man hinh dang hien "
                        + r.expectedPreviousDebt().stripTrailingZeros().toPlainString() + ", hien tai la "
                        + previous.stripTrailingZeros().toPlainString() + ". Da tai lai, kiem tra roi chot lai.");
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

            debts.apply(c, DebtLedger.Type.ORDER, charge, o.getId(), "Hoa don " + o.getCode());
            BigDecimal applied = paid.min(payable); // phan tien thua tra lai khach, khong tru vao no
            if (applied.signum() > 0) {
                debts.apply(c, DebtLedger.Type.PAYMENT, applied.negate(), o.getId(), "Thanh toan hoa don " + o.getCode());
            }
        } else {
            // Khach le: bat buoc co ten va PHAI TRA DU (tien hang + ship). Khac app cu (cho tra thieu, phan thieu chi ghi
            // tren hoa don, khong ai theo doi de thu) -> muon ghi no thi chon / tao khach hang.
            if (r.customerName() == null || r.customerName().isBlank())
                throw ApiException.badRequest("Vui long nhap ten khach le hoac chon khach hang");
            if (paid.compareTo(charge) < 0)
                throw ApiException.badRequest("Khach le phai tra du " + charge.stripTrailingZeros().toPlainString()
                        + " (con thieu " + charge.subtract(paid).stripTrailingZeros().toPlainString()
                        + "). Muon ghi no thi chon hoac tao khach hang");
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
        if (o.getStatus() != Order.Status.PAID) throw ApiException.conflict("Chi huy duoc hoa don da chot");
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
        if (o.getStatus() != Order.Status.PAID) throw ApiException.conflict("Chi huy duoc hoa don da chot");
        if (o.getCustomerId() != null) {
            Customer c = customers.findById(o.getCustomerId()).orElseThrow();
            CancelEffect e = effect(o, c);
            String note = "Huy hoa don " + o.getCode()
                    + (e.refund().signum() > 0 ? "; tra lai khach " + e.refund().stripTrailingZeros().toPlainString() + " tien mat" : "");
            if (e.debtReversal().signum() != 0 || e.refund().signum() > 0)
                debts.apply(c, DebtLedger.Type.ADJUST, e.debtReversal().negate(), o.getId(), note);
            o.setRefundAmount(e.refund());
        }
        o.setStatus(Order.Status.CANCELLED);
        o.getItems().size();
        return o;
    }
}
