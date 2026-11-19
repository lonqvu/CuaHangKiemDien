package com.shop.report.jasper;

import com.shop.common.ApiException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gui Zalo tu may tinh (giong app cu: chay Zalo.exe kem file PDF -> Zalo hien danh sach nguoi nhan).
 * Trinh duyet khong duoc chay chuong trinh tren may, nen:
 *  1. Trang web (da dang nhap) tai PDF hoa don / phieu thu / sao ke roi gui len day -> nhan ma ngau nhien (10 phut).
 *  2. Trang web mo lien ket nskd-zalo://send/{ma}/{ten file} -> tien ich "Gui Zalo" cai 1 lan tren may tinh
 *     tai file qua GET /api/public/share/{ma} roi chay Zalo.exe "file".
 * File chi giu trong bo nho, het han sau 10 phut; ma 32 byte ngau nhien (khong doan duoc).
 */
@RestController
public class ShareController {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final long TTL_MS = 10 * 60_000;
    static final int MAX_ITEMS = 200;

    private record Item(byte[] data, MediaType type, String name, long expires) {}

    private final Map<String, Item> items = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public record Shared(String token, String name) {}

    @PostMapping(value = "/api/share", consumes = MediaType.ALL_VALUE)
    public Shared put(@RequestBody byte[] data, @RequestParam String name) {
        if (data == null || data.length == 0) throw ApiException.badRequest("Chưa có file");
        if (data.length > MAX_BYTES) throw ApiException.badRequest("File quá lớn");
        MediaType type = sniff(data);
        if (type == null) throw ApiException.badRequest("Chỉ gửi được PDF hoặc ảnh PNG");
        String ext = type.equals(MediaType.APPLICATION_PDF) ? ".pdf" : ".png";
        // ten file an toan cho Windows / dong lenh: chu, so, _ . - (bo dau tieng Viet)
        String base = java.text.Normalizer.normalize(name == null ? "" : name.replaceAll("\\.(pdf|png)$", ""), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D').replaceAll("[^A-Za-z0-9_.-]+", "_");
        base = base.replaceAll("^[._-]+", "");
        if (base.isEmpty()) base = "HoaDon";
        if (base.length() > 60) base = base.substring(0, 60);

        long now = System.currentTimeMillis();
        items.values().removeIf(i -> i.expires() < now);
        if (items.size() >= MAX_ITEMS) throw ApiException.conflict("Đang có quá nhiều file chờ gửi, thử lại sau ít phút");
        byte[] b = new byte[32];
        random.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        items.put(token, new Item(data, type, base + ext, now + TTL_MS));
        return new Shared(token, base + ext);
    }

    @GetMapping("/api/public/share/{token}")
    public ResponseEntity<byte[]> get(@PathVariable String token) {
        Item i = items.get(token);
        if (i == null || i.expires() < System.currentTimeMillis()) {
            items.remove(token);
            throw ApiException.notFound("Liên kết đã hết hạn. Bấm Gửi Zalo lại trên trang web.");
        }
        return ResponseEntity.ok().contentType(i.type())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(i.name(), StandardCharsets.UTF_8).build().toString())
                .body(i.data());
    }

    private static MediaType sniff(byte[] d) {
        if (d.length > 4 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F') return MediaType.APPLICATION_PDF;
        if (d.length > 8 && (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return MediaType.IMAGE_PNG;
        return null;
    }
}
