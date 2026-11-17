package com.shop.supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tim NCC khong dau, theo tu (nhu tim khach / san pham): man Nhap hang go "kho thep" + Enter phai chon dung. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SupplierSearchTest {
    @Autowired SupplierController controller;

    private void add(String name, String phone) {
        controller.create(new SupplierController.SupplierRequest(name, phone, null, null));
    }

    private List<String> names(String q) {
        return controller.list(q, false, 0, 20, "").items().stream().map(Supplier::getName).filter(n -> n.startsWith("ZZ")).toList();
    }

    @Test
    void searchWithoutMarks_rankedByWord() {
        add("ZZ Kho thép Minh Long", "0912000222");
        add("ZZ Đại lý tôn Phương Nam", "0283999111");
        add("ZZ Thép Khôi", null);
        assertEquals(List.of("ZZ Kho thép Minh Long", "ZZ Thép Khôi"), names("kho thep")); // khop nguyen tu "kho" truoc "Khôi"
        assertEquals("ZZ Đại lý tôn Phương Nam", names("dai ly ton").get(0));
        assertEquals(List.of("ZZ Đại lý tôn Phương Nam"), names("9111")); // 4 so cuoi SDT
        assertEquals("ZZ Thép Khôi", names("thep").get(0)); // khop dau ten len truoc
        assertEquals(2, names("thep").size());
        assertTrue(names("").size() >= 3);
    }
}
