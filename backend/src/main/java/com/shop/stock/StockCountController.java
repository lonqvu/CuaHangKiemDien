package com.shop.stock;

import com.shop.common.ApiException;
import com.shop.common.PageDto;
import com.shop.product.Product;
import com.shop.product.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Kiem ke: tao phieu (danh sach san pham dang ban, co the theo loai) -> nhap so dem thuc te -> ghi so.
 * Chenh lech tinh voi ton LUC GHI SO (khong phai luc tao phieu), vi trong luc dem quay van ban hang.
 * Phieu kiem ke dau tien dung lam ton dau ky: nhap them gia von cho phan thua.
 */
@RestController
@RequestMapping("/api/stock-counts")
public class StockCountController {
    private final StockCountRepository counts;
    private final ProductRepository products;
    private final StockBalanceRepository balances;
    private final StockService stock;

    public StockCountController(StockCountRepository counts, ProductRepository products, StockBalanceRepository balances, StockService stock) {
        this.counts = counts; this.products = products; this.balances = balances; this.stock = stock;
    }

    public record CreateRequest(String note, Long categoryId) {}
    public record LineUpdate(Long lineId, BigDecimal countedQty, BigDecimal unitCost) {}
    public record CountRow(Long id, String code, StockCount.Status status, String note, int lineCount, long countedLines,
                           String createdBy, LocalDateTime createdAt, LocalDateTime postedAt) {}

    private StockCount find(Long id) { return counts.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu kiểm kê")); }

    private StockCount findDraft(Long id) {
        StockCount c = find(id);
        if (c.getStatus() != StockCount.Status.DRAFT) throw ApiException.conflict("Phiếu kiểm kê đã ghi sổ, không sửa được");
        return c;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageDto<CountRow> list(@RequestParam(defaultValue = "POSTED") StockCount.Status status, @RequestParam(defaultValue = "") String q,
                                  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var p = counts.page(status, PageDto.like(q), PageDto.pageable(page, size, null, Map.of(),
                Sort.by(Sort.Direction.DESC, status == StockCount.Status.DRAFT ? "createdAt" : "postedAt", "id")));
        return PageDto.of(p, c -> new CountRow(c.getId(), c.getCode(), c.getStatus(), c.getNote(), c.getLines().size(),
                c.getLines().stream().filter(l -> l.getCountedQty() != null).count(), c.getCreatedBy(), c.getCreatedAt(), c.getPostedAt()),
                Map.of("countByStatus", Map.of("DRAFT", counts.countByStatus(StockCount.Status.DRAFT),
                        "POSTED", counts.countByStatus(StockCount.Status.POSTED))));
    }

    /** Chi tiet phieu. Phieu nhap: systemQty = ton hien tai (de doi chieu khi dem). */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public StockCount get(@PathVariable Long id) {
        StockCount c = find(id);
        c.getLines().size(); // nap dong trong transaction (ca phieu da ghi so) - tranh loi lazy khi ghi JSON
        if (c.getStatus() == StockCount.Status.DRAFT) {
            var bal = stock.balancesOf(c.getLines().stream().map(StockCountLine::getProductId).toList());
            c.getLines().forEach(l -> {
                StockBalance b = bal.get(l.getProductId());
                l.setSystemQty(b == null ? BigDecimal.ZERO : b.getOnHand()); // chi de hien thi (readOnly, khong luu)
            });
        }
        return c;
    }

    @PostMapping
    @Transactional
    public Map<String, Long> create(@RequestBody CreateRequest r, Authentication a) {
        List<Product> list = products.findAll(Sort.by("name")).stream().filter(Product::isActive)
                .filter(p -> r.categoryId() == null || (p.getCategory() != null && p.getCategory().getId().equals(r.categoryId()))).toList();
        if (list.isEmpty()) throw ApiException.badRequest("Không có sản phẩm đang bán để kiểm kê");
        StockCount c = new StockCount();
        c.setNote(r.note());
        c.setCreatedBy(ReceiptController.who(a));
        for (Product p : list) {
            StockCountLine l = new StockCountLine();
            l.setCount(c);
            l.setProductId(p.getId());
            l.setProductName(p.getName());
            l.setUnit(p.getUnit());
            c.getLines().add(l);
        }
        return Map.of("id", counts.save(c).getId());
    }

    /** Luu so dem (nhieu lan, truoc khi ghi so). countedQty null = chua dem. */
    @PutMapping("/{id}/lines")
    @Transactional
    public void saveLines(@PathVariable Long id, @RequestBody List<LineUpdate> updates) {
        StockCount c = findDraft(id);
        Map<Long, StockCountLine> byId = c.getLines().stream().collect(Collectors.toMap(StockCountLine::getId, Function.identity()));
        for (LineUpdate u : updates) {
            StockCountLine l = byId.get(u.lineId());
            if (l == null) continue;
            if (u.countedQty() != null && u.countedQty().signum() < 0) throw ApiException.badRequest("Số đếm của \"" + l.getProductName() + "\" không được âm");
            if (u.unitCost() != null && u.unitCost().signum() < 0) throw ApiException.badRequest("Giá vốn của \"" + l.getProductName() + "\" không hợp lệ");
            l.setCountedQty(u.countedQty());
            l.setUnitCost(u.unitCost());
        }
    }

    @PostMapping("/{id}/post")
    @Transactional
    public StockCount post(@PathVariable Long id, Authentication a) {
        StockCount c = findDraft(id);
        List<StockCountLine> counted = c.getLines().stream().filter(l -> l.getCountedQty() != null).toList();
        if (counted.isEmpty()) throw ApiException.badRequest("Chưa nhập số đếm cho mặt hàng nào");
        c.setCode(String.format("KK%06d", counts.nextCode()));
        stock.lockAll(counted.stream().map(StockCountLine::getProductId).toList());
        String by = ReceiptController.who(a);
        for (StockCountLine l : counted) {
            BigDecimal system = balances.findById(l.getProductId()).map(StockBalance::getOnHand).orElse(BigDecimal.ZERO);
            BigDecimal diff = l.getCountedQty().setScale(3, RoundingMode.HALF_UP).subtract(system);
            l.setSystemQty(system);
            l.setDiff(diff);
            if (diff.signum() != 0) {
                stock.apply(new StockService.Move(l.getProductId(), diff, diff.signum() > 0 ? l.getUnitCost() : null,
                        StockMovement.Type.COUNT, StockService.REF_COUNT, c.getId(), l.getId(), c.getCode(),
                        c.getNote() == null || c.getNote().isBlank() ? "Kiểm kê" : c.getNote(), by));
            }
        }
        c.setStatus(StockCount.Status.POSTED);
        c.setPostedAt(LocalDateTime.now());
        return c;
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) { counts.delete(findDraft(id)); }
}
