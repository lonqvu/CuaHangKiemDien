package com.shop.product;

import com.shop.customer.Customer;
import com.shop.customer.CustomerController;
import com.shop.customer.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.shop.order.OrderService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductSearchTest {
    @Autowired ProductController productController;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired CustomerController customerController;
    @Autowired CustomerRepository customers;
    @Autowired OrderService orders;

    private Product product(String name, Category c, boolean active) {
        Product p = new Product();
        p.setName(name); p.setUnit("m"); p.setPrice(BigDecimal.TEN); p.setCategory(c); p.setActive(active);
        return products.save(p);
    }

    private List<String> names(String q) {
        return productController.list(q, 0, true, 0, 100, "", null, false).items().stream().map(ProductController.ProductDto::name)
                .filter(n -> n.startsWith("ZZ ")).toList(); // chi xet du lieu cua test nay
    }

    @Test
    void productSearch_accentInsensitive_anyOrder_byCategory_skipsInactive() {
        Category nhom = new Category();
        nhom.setName("Nhôm ZZ");
        nhom = categories.save(nhom);
        product("ZZ Tôn lạnh 0,4 ly", null, true);
        product("ZZ Xingfa hệ 55", nhom, true);
        product("ZZ Tôn cũ ngừng bán", null, false);

        assertEquals(List.of("ZZ Tôn lạnh 0,4 ly"), names("ton lanh"));
        assertEquals(List.of("ZZ Tôn lạnh 0,4 ly"), names("tôn 0,4"));
        assertEquals(List.of("ZZ Tôn lạnh 0,4 ly"), names("lạnh tôn"));
        assertEquals(List.of("ZZ Xingfa hệ 55"), names("nhom zz 55"));
        assertTrue(names("ngung ban").isEmpty());
    }

    @Test
    void customerSearch_accentInsensitive_byPhone() {
        Customer c = new Customer();
        c.setName("ZZ Anh Tuấn thợ hàn"); c.setPhone("0905999111"); c.setDebt(BigDecimal.ZERO);
        customers.save(c);

        assertTrue(customerController.list("tuan han", false, 0, 100, "").items().stream().anyMatch(x -> x.getName().equals("ZZ Anh Tuấn thợ hàn")));
        assertTrue(customerController.list("999111", false, 0, 100, "").items().stream().anyMatch(x -> x.getName().equals("ZZ Anh Tuấn thợ hàn")));
        assertTrue(customerController.list("tuan 0905999", false, 0, 100, "").items().stream().anyMatch(x -> x.getName().equals("ZZ Anh Tuấn thợ hàn")));
    }

    private void sell(Product p, Long customerId, int times) {
        for (int k = 0; k < times; k++) {
            var o = orders.createDraft("thu");
            orders.addItem(o.getId(), new OrderService.ItemRequest(p.getId(), BigDecimal.ONE, 0, null, null));
            orders.checkout(o.getId(), new OrderService.CheckoutRequest(customerId, customerId == null ? "Khách lẻ" : null, null, null,
                    BigDecimal.ZERO, customerId == null ? p.getPrice() : BigDecimal.ZERO, null));
        }
    }

    /** 3. Da chon khach: hang khach hay mua len dau (Enter chon dung), kem so lan mua; khong chon khach: hang ban chay cua hang. */
    @Test
    void customerFavoritesFirst() {
        Product a = product("ZZ Thép hộp 20x40", null, true);
        Product b = product("ZZ Thép hộp 40x80", null, true);
        Customer c = new Customer();
        c.setName("ZZ Anh Hùng " + java.util.UUID.randomUUID());
        c = customers.save(c);
        sell(a, null, 3);          // ca cua hang ban a nhieu hon
        sell(b, c.getId(), 2);     // nhung anh Hung hay mua b

        var plain = productController.list("zz thep hop", 0, true, 0, 10, "", null, false).items();
        assertEquals("ZZ Thép hộp 20x40", plain.get(0).name());
        assertNull(plain.get(0).boughtCount());

        var forHung = productController.list("zz thep hop", 0, true, 0, 10, "", c.getId(), false).items();
        assertEquals("ZZ Thép hộp 40x80", forHung.get(0).name());
        assertEquals(2L, forHung.get(0).boughtCount());
        assertNotNull(forHung.get(0).lastBought());
        assertNull(forHung.get(1).boughtCount());

        // o tim trong: hien san hang khach hay mua dau tien
        var empty = productController.list("", 0, true, 0, 100, "", c.getId(), false).items();
        assertEquals("ZZ Thép hộp 40x80", empty.get(0).name());
        // o tim trong, khong chon khach, popular: hang ban chay truoc (a ban 3 lan > b 2 lan)
        var pop = productController.list("", 0, true, 0, 100, "", null, true).items().stream().map(ProductController.ProductDto::name)
                .filter(n -> n.startsWith("ZZ Thép")).toList();
        assertEquals(List.of("ZZ Thép hộp 20x40", "ZZ Thép hộp 40x80"), pop);
    }
}
