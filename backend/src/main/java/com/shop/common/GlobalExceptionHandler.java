package com.shop.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Moi loi deu tra ve {"message": "..."} tieng Viet de frontend hien thang cho nguoi dung.
 * Bat ca loi khong luong truoc (Exception): neu de lot xuong /error, Spring Security tra 401 va frontend dang xuat nguoi dung.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Ten truong trong request -> nhan nguoi dung nhin thay tren form. */
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("name", "Tên"), Map.entry("unit", "Đơn vị"), Map.entry("price", "Giá bán"),
            Map.entry("amount", "Số tiền"), Map.entry("debt", "Số nợ"), Map.entry("username", "Tên đăng nhập"),
            Map.entry("fullName", "Họ tên"), Map.entry("role", "Vai trò"), Map.entry("password", "Mật khẩu"),
            Map.entry("oldPassword", "Mật khẩu hiện tại"), Map.entry("newPassword", "Mật khẩu mới"));

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream().map(GlobalExceptionHandler::describe)
                .distinct().collect(Collectors.joining(". "));
        return ResponseEntity.badRequest().body(Map.of("message", msg));
    }

    private static String describe(FieldError f) {
        String label = LABELS.getOrDefault(f.getField(), f.getField());
        String code = f.getCode() == null ? "" : f.getCode();
        String def = f.getDefaultMessage();
        // Thong bao viet san tren annotation (co dau) thi dung luon
        if (def != null && !def.isBlank() && !def.matches("[\\x00-\\x7F]*")) return def;
        return switch (code) {
            case "NotBlank", "NotNull", "NotEmpty" -> label + ": chưa nhập";
            case "Size" -> label + (f.getField().toLowerCase().contains("password") ? ": cần ít nhất 6 ký tự" : ": quá dài");
            case "DecimalMin", "Min" -> label + ": phải lớn hơn 0";
            default -> label + ": không hợp lệ";
        };
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, String>> badParam(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message", "Dữ liệu gửi lên không hợp lệ. Kiểm tra lại các ô đã nhập."));
    }

    // Hai may cung sua mot khach (cong no) -> bao nguoi dung thu lai
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Map<String, String>> locking(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Dữ liệu vừa được người khác thay đổi. Tải lại rồi thử lại."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Tài khoản của bạn không có quyền làm việc này"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, String>> notFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Không tìm thấy đường dẫn này"));
    }

    // Goi sai phuong thuc (GET vao duong chi co PUT...) / sai kieu du lieu: loi phia goi, khong phai loi may chu (truoc: 500 + log ERROR)
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, String>> method(org.springframework.web.HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("message", "Thao tác không được hỗ trợ ở đường dẫn này"));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, String>> mediaType(org.springframework.web.HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of("message", "Dữ liệu gửi lên không đúng định dạng"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpected(Exception e) {
        log.error("Loi khong luong truoc", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message",
                "Máy chủ gặp lỗi khi xử lý yêu cầu. Thử lại; nếu vẫn lỗi, báo quản trị kèm thời điểm xảy ra."));
    }
}
