package com.shop.product;

import com.shop.customer.Customer;
import com.shop.customer.CustomerController;
import com.shop.customer.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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

    private Product product(String name, Category c, boolean active) {
        Product p = new Product();
        p.setName(name); p.setUnit("m"); p.setPrice(BigDecimal.TEN); p.setCategory(c); p.setActive(active);
        return products.save(p);
    }

    private List<String> names(String q) {
        return productController.list(q, 0, true, 0, 100, "").items().stream().map(ProductController.ProductDto::name)
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
}
