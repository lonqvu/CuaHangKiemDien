package com.shop.common;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Tim kiem kieu quay ban hang: khong phan biet dau ("ton lanh" thay "Tôn lạnh"),
 * tach theo tu va khong can dung thu tu ("tôn 0,4" va "lanh ton" deu thay "Tôn lạnh 0,4 ly").
 * Loc trong Java (khong dung unaccent cua Postgres) de chay giong nhau tren Postgres va H2 (test);
 * danh muc san pham / khach hang cua cua hang chi vai tram - vai nghin dong nen loc trong bo nho la du nhanh.
 */
public final class TextSearch {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private TextSearch() {}

    /** Bo dau tieng Viet, chu thuong, gop khoang trang. */
    public static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = MARKS.matcher(n).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
        return SPACES.matcher(n.toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
    }

    /** Cac tu cua cau tim (da bo dau); rong = khong loc. */
    public static List<String> tokens(String query) {
        String f = fold(query);
        return f.isEmpty() ? List.of() : Arrays.asList(f.split(" "));
    }

    /** True neu moi tu cua cau tim deu nam trong it nhat mot truong. */
    public static boolean matches(List<String> tokens, String... fields) {
        if (tokens.isEmpty()) return true;
        String hay = fold(String.join(" ", Arrays.stream(fields).map(f -> f == null ? "" : f).toList()));
        return tokens.stream().allMatch(hay::contains);
    }

    public static <T> List<T> filter(List<T> rows, String query, Function<T, String[]> fields) {
        List<String> t = tokens(query);
        if (t.isEmpty()) return rows;
        return rows.stream().filter(r -> matches(t, fields.apply(r))).toList();
    }
}
