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
        orders.saveAndFlush(o); // de moi dong moi co id tra ve cho client
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
            // Khach le (app cu): bat buoc co ten; duoc tra thieu, phan con lai chi ghi tren hoa don, khong vao cong no
            if (r.customerName() == null || r.customerName().isBlank())
                throw ApiException.badRequest("Vui long nhap ten khach le hoac chon khach hang");
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
        if (o.getStatus() != Order.Status.PAID) throw ApiException.conflict("Chi huy duoc hoa don da chot");
        if (o.getCustomerId() != null) {
            Customer c = customers.findById(o.getCustomerId()).orElseThrow();
            BigDecimal netDebtAdded = o.getDebtAfter().subtract(o.getPreviousDebt()); // hoa don nay lam no thay doi bao nhieu
            debts.apply(c, DebtLedger.Type.ADJUST, netDebtAdded.negate(), o.getId(), "Huy hoa don " + o.getCode());
        }
        o.setStatus(Order.Status.CANCELLED);
        o.getItems().size();
        return o;
    }
}
