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
        assertEquals(rows, TextSearch.filter(rows, "   ", r -> new String[]{r}));
        assertEquals(List.of("b"), TextSearch.filter(rows, "B", r -> new String[]{r}));
    }

    private static List<String> rank(String q, String... names) {
        return TextSearch.rank(List.of(names), q, n -> new String[]{n}, n -> 0);
    }

    @Test
    void rank_wholeWordBeforeInsideWord() {
        // "ton" nam giua "Carton" -> xuong cuoi, Enter khong chon nham
        assertEquals("Tôn lạnh 0,4 ly", rank("ton", "Carton chống ẩm", "Tôn lạnh 0,4 ly").get(0));
        assertEquals("Carton chống ẩm", rank("ton", "Carton chống ẩm", "Tôn lạnh 0,4 ly").get(1));
    }

    @Test
    void rank_exactNumberBeforeLongerNumber() {
        // "0,4" dung hon "0,45"; "1,4" dung hon "1,45"
        assertEquals(List.of("Tôn lạnh 0,4 ly", "Tôn lạnh 0,45 ly"), rank("ton lanh 0,4", "Tôn lạnh 0,45 ly", "Tôn lạnh 0,4 ly"));
        assertEquals("Hộp sắt 40x80 dày 1,4", rank("hop 40x80 1,4", "Hộp sắt 40x80 dày 1,45", "Hộp sắt 40x80 dày 1,4").get(0));
    }

    @Test
    void rank_earlierInNameWins_whenScoresTie() {
        // ca hai deu co tu "40", "40x80" co 40 dung truoc
        assertEquals("Hộp sắt 40x80 dày 1,4", rank("hop 40 1,4", "Hộp sắt 140x40 dày 1,4", "Hộp sắt 40x80 dày 1,4").get(0));
    }

    @Test
    void rank_popularBreaksTies_andEmptyQueryListsBestSellersFirst() {
        List<String> names = List.of("Tôn kẽm 0,4 ly", "Tôn lạnh 0,4 ly");
        java.util.Map<String, Long> sold = java.util.Map.of("Tôn lạnh 0,4 ly", 30L);
        assertEquals("Tôn lạnh 0,4 ly", TextSearch.rank(names, "ton 0,4", n -> new String[]{n}, n -> sold.getOrDefault(n, 0L)).get(0));
        assertEquals("Tôn lạnh 0,4 ly", TextSearch.rank(names, "", n -> new String[]{n}, n -> sold.getOrDefault(n, 0L)).get(0));
        // nhung khop dung van thang ban nhieu: go "kem" thi khong ra ton lanh
        assertEquals(List.of("Tôn kẽm 0,4 ly"), TextSearch.rank(names, "kem", n -> new String[]{n}, n -> sold.getOrDefault(n, 0L)));
    }

    @Test
    void rank_nameBeatsCategory() {
        // "nhom" trung ten loai hang cua moi mat hang nhom, nhung mat hang co chu Nhom trong ten len truoc
        List<String[]> rows = List.of(new String[]{"Xingfa hệ 55", "Nhôm"}, new String[]{"Nhôm hộp 25x50", "Nhôm"});
        assertEquals("Nhôm hộp 25x50", TextSearch.rank(rows, "nhom", r -> r, r -> 0).get(0)[0]);
    }
}
