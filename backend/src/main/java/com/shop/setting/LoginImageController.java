package com.shop.setting;

import com.shop.common.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Anh nen man dang nhap.
 *  GET    /api/public/login-image   : ai cung xem duoc (man dang nhap chua co phien); 404 = chua dat -> giao dien mac dinh
 *  PUT    /api/settings/login-image : quan tri dat anh (than request = file anh JPEG / PNG / WebP, toi da 3 MB)
 *  DELETE /api/settings/login-image : quay ve giao dien mac dinh
 */
@RestController
@RequestMapping("/api")
public class LoginImageController {
    static final int MAX_BYTES = 3 * 1024 * 1024;
    private final LoginImageRepository images;

    public LoginImageController(LoginImageRepository images) { this.images = images; }

    public record Info(boolean hasImage, String updatedAt, String updatedBy, int size) {}

    @GetMapping("/public/login-image")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> get(@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return images.findById(LoginImage.ID).map(img -> {
            String etag = "\"" + img.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + "\"";
            // no-cache: trinh duyet luon hoi lai (304 neu chua doi) -> thay anh la moi may thay ngay
            if (etag.equals(ifNoneMatch)) return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).<byte[]>build();
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(img.getContentType()))
                    .cacheControl(CacheControl.noCache()).eTag(etag).body(img.getData());
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/settings/login-image")
    @Transactional(readOnly = true)
    public Info info() {
        return images.findById(LoginImage.ID)
                .map(i -> new Info(true, i.getUpdatedAt().toString(), i.getUpdatedBy(), i.getData().length))
                .orElse(new Info(false, null, null, 0));
    }

    @PutMapping(value = "/settings/login-image", consumes = MediaType.ALL_VALUE)
    @Transactional
    public Info put(@RequestBody byte[] data, Authentication auth) {
        if (data == null || data.length == 0) throw ApiException.badRequest("Chưa chọn ảnh");
        if (data.length > MAX_BYTES) throw ApiException.badRequest("Ảnh quá lớn (tối đa 3 MB). Chọn ảnh nhỏ hơn.");
        String type = sniff(data);
        if (type == null) throw ApiException.badRequest("Chỉ nhận ảnh JPG, PNG hoặc WebP");
        LoginImage img = images.findById(LoginImage.ID).orElseGet(LoginImage::new);
        img.setContentType(type);
        img.setData(data);
        img.setUpdatedAt(LocalDateTime.now());
        img.setUpdatedBy(auth.getName());
        images.save(img);
        return info();
    }

    @DeleteMapping("/settings/login-image")
    @Transactional
    public Map<String, Boolean> delete() {
        images.deleteById(LoginImage.ID);
        return Map.of("hasImage", false);
    }

    /** Loai anh theo noi dung file (khong tin duoi file / Content-Type gui len). null = khong phai JPEG/PNG/WebP. */
    static String sniff(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xff) == 0xFF && (d[1] & 0xff) == 0xD8 && (d[2] & 0xff) == 0xFF) return "image/jpeg";
        if (d.length >= 8 && (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P')
            return "image/webp";
        return null;
    }
}
