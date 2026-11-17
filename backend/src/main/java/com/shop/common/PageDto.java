package com.shop.common;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Ket qua danh sach co phan trang. summary: so lieu tong hop tren TOAN BO ket qua loc (khong chi trang hien tai),
 * vd tong tien hang, tong no - de frontend khong phai tai het du lieu ve cong.
 */
public record PageDto<T>(List<T> items, long total, int page, int size, Map<String, Object> summary) {

    public static final int MAX_SIZE = 100;

    public static <E, T> PageDto<T> of(Page<E> p, Function<E, T> map, Map<String, Object> summary) {
        return new PageDto<>(p.getContent().stream().map(map).toList(), p.getTotalElements(),
                p.getNumber(), p.getSize(), summary == null ? Map.of() : summary);
    }

    /**
     * page bat dau tu 0; size gioi han 1..100. sort dang "truong,asc|desc"; chi nhan truong trong whitelist
     * (khoa la ten tham so tu frontend, gia tri la thuoc tinh entity) de khong lo sap xep theo cot tuy y.
     */
    public static Pageable pageable(int page, int size, String sort, Map<String, String> allowed, Sort fallback) {
        Sort s = fallback;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String prop = allowed.get(parts[0]);
            if (prop != null) {
                Sort.Direction dir = parts.length > 1 && parts[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
                s = Sort.by(dir, prop).and(fallback);
            }
        }
        return PageRequest.of(Math.max(0, page), Math.min(MAX_SIZE, Math.max(1, size)), s);
    }

    /** Cat trang tu danh sach da loc + xep hang trong bo nho (tim kiem khong dau: xem TextSearch). */
    public static <E, T> PageDto<T> ofList(List<E> all, int page, int size, Function<E, T> map, Map<String, Object> summary) {
        int sz = Math.min(MAX_SIZE, Math.max(1, size)), pg = Math.max(0, page);
        int from = Math.min(all.size(), pg * sz), to = Math.min(all.size(), from + sz);
        return new PageDto<>(all.subList(from, to).stream().map(map).toList(), all.size(), pg, sz, summary == null ? Map.of() : summary);
    }

    /** Chuoi tim kiem -> mau LIKE da ha chu thuong; rong thi tra "" (cau truy van bo qua dieu kien). */
    public static String like(String q) {
        return q == null || q.isBlank() ? "" : "%" + q.trim().toLowerCase() + "%";
    }
}
