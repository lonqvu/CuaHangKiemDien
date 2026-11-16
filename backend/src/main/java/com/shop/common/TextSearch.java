package com.shop.common;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tim kiem kieu quay ban hang: khong phan biet dau ("ton lanh" thay "Tôn lạnh"),
 * tach theo tu va khong can dung thu tu ("tôn 0,4" va "lanh ton" deu thay "Tôn lạnh 0,4 ly").
 *
 * Man Ban hang bam Enter la chon ket qua DAU TIEN, nen thu tu phai dung y nguoi ban:
 *   khop nguyen tu  >  khop dau tu  >  khop giua tu ("ton" trong "Carton" xuong cuoi),
 *   "0,4" khop nguyen tu "0,4" xep tren "0,45"; "40" khop "40x80" xep tren "140x40".
 * Hoa nhau thi mat hang ban nhieu len truoc, roi theo vi tri khop, roi theo ten.
 *
 * Loc trong Java (khong dung unaccent cua Postgres) de chay giong nhau tren Postgres va H2 (test);
 * danh muc cua cua hang chi vai tram - vai nghin dong nen loc trong bo nho la du nhanh.
 */
public final class TextSearch {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    /** Mot "tu" de cham diem: day chu cai, hoac so (co the co dau , . thap phan). "40x80" -> 40 | x | 80. */
    private static final Pattern WORD = Pattern.compile("\\d+(?:[.,]\\d+)*|[a-z]+");

    // diem cho tung tu cua cau tim, truong chinh (ten) cao hon truong phu (loai hang, SDT)
    private static final int EXACT = 6, PREFIX = 4, INSIDE = 1;

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
        return score(tokens, fields) >= 0;
    }

    /**
     * Diem khop: -1 neu co tu khong tim thay o truong nao. fields[0] la truong chinh (ten), cac truong sau la phu (diem / 2).
     * Moi tu cua cau tim lay diem cao nhat trong cac tu cua cac truong.
     */
    public static int score(List<String> tokens, String... fields) {
        return match(tokens, fields)[0];
    }

    /** {diem, vi tri}: vi tri = tong so thu tu cua tu khop tot nhat trong ten (cang nho = khop cang som trong ten). */
    private static int[] match(List<String> tokens, String... fields) {
        if (tokens.isEmpty()) return new int[]{0, 0};
        List<List<String>> words = new ArrayList<>();
        List<String> folded = new ArrayList<>();
        for (String f : fields) {
            String s = fold(f);
            folded.add(s);
            words.add(words(s));
        }
        int total = 0, pos = 0;
        for (String t : tokens) {
            int best = -1, bestPos = 0;
            for (int i = 0; i < fields.length; i++) {
                int[] m = tokenScore(t, folded.get(i), words.get(i));
                int sc = m[0];
                if (sc < 0) continue;
                if (i > 0) sc = Math.max(1, sc / 2);
                if (sc > best) { best = sc; bestPos = i == 0 ? m[1] : 99; }
            }
            if (best < 0) return new int[]{-1, 0};
            total += best;
            pos += bestPos;
        }
        return new int[]{total, pos};
    }

    /** {diem, so thu tu cua tu khop tot nhat}; diem -1 = khong co trong truong. */
    private static int[] tokenScore(String token, String field, List<String> fieldWords) {
        if (!field.contains(token)) return new int[]{-1, 0};
        // cau tim co the gom nhieu "tu" (vd "40x80", "0,4ly"): xet tung tu con, lay muc thap nhat
        List<String> parts = words(token);
        if (parts.isEmpty()) return new int[]{INSIDE, 0}; // chi co ky tu dac biet
        int min = EXACT, at = 0;
        for (int k = 0; k < parts.size(); k++) {
            String p = parts.get(k);
            int s = INSIDE, where = fieldWords.size();
            for (int j = 0; j < fieldWords.size(); j++) {
                String w = fieldWords.get(j);
                int ws = w.equals(p) ? EXACT : w.startsWith(p) ? PREFIX : w.contains(p) ? INSIDE : -1;
                if (ws > s || (ws == s && where == fieldWords.size())) { s = Math.max(s, ws); where = j; }
                if (ws == EXACT) break;
            }
            min = Math.min(min, s);
            if (k == 0) at = where;
        }
        return new int[]{min, at};
    }

    private static List<String> words(String folded) {
        List<String> out = new ArrayList<>();
        Matcher m = WORD.matcher(folded);
        while (m.find()) out.add(m.group());
        return out;
    }

    public static <T> List<T> filter(List<T> rows, String query, Function<T, String[]> fields) {
        return rank(rows, query, fields, r -> 0);
    }

    /**
     * Loc + xep hang. Cau tim rong: giu tat ca, mat hang ban nhieu len truoc (giu thu tu goc khi bang nhau).
     * @param popularity so lan ban (cang lon cang len truoc khi diem khop bang nhau)
     */
    public static <T> List<T> rank(List<T> rows, String query, Function<T, String[]> fields, ToLongFunction<T> popularity) {
        List<String> t = tokens(query);
        record Hit<T>(T row, int score, long pop, int pos, int idx) {}
        List<Hit<T>> hits = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            T r = rows.get(i);
            String[] f = fields.apply(r);
            int[] m = match(t, f);
            if (m[0] < 0) continue;
            hits.add(new Hit<>(r, m[0], popularity.applyAsLong(r), m[1], i));
        }
        hits.sort(Comparator.<Hit<T>>comparingInt(h -> -h.score())
                .thenComparingLong(h -> -h.pop())
                .thenComparingInt(Hit::pos)
                .thenComparingInt(Hit::idx));
        return hits.stream().map(Hit::row).toList();
    }
}
