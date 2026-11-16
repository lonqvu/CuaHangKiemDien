package com.shop.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextSearchTest {
    private static boolean m(String q, String... fields) { return TextSearch.matches(TextSearch.tokens(q), fields); }

    @Test
    void fold_removesVietnameseMarks() {
        assertEquals("ton lanh 0,4 ly", TextSearch.fold("  Tôn  LẠNH 0,4 ly "));
        assertEquals("duong dong", TextSearch.fold("Đường đồng"));
    }

    @Test
    void matchesWithoutDiacritics() {
        assertTrue(m("ton lanh", "Tôn lạnh 0,4 ly"));
        assertTrue(m("TÔN", "Tôn lạnh 0,4 ly"));
        assertTrue(m("dong", "Ống đồng"));
    }

    @Test
    void matchesWordsInAnyOrderAndNonContiguous() {
        assertTrue(m("tôn 0,4", "Tôn lạnh 0,4 ly"));
        assertTrue(m("lanh ton", "Tôn lạnh 0,4 ly"));
        assertTrue(m("hop 40x80 1,4", "Hộp sắt 40x80 dày 1,4"));
        assertFalse(m("tôn 0,5", "Tôn lạnh 0,4 ly"));
    }

    @Test
    void wordsMayComeFromDifferentFields() {
        assertTrue(m("nhom 55", "Xingfa hệ 55", "Nhôm"));
        assertTrue(m("tuan 0905", "Anh Tuấn thợ hàn", "0905123456"));
        assertFalse(m("anything"));
    }

    @Test
    void emptyQueryKeepsEverything() {
        List<String> rows = List.of("a", "b");
        assertSame(rows, TextSearch.filter(rows, "   ", r -> new String[]{r}));
        assertEquals(List.of("b"), TextSearch.filter(rows, "B", r -> new String[]{r}));
    }
}
