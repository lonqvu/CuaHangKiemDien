package com.shop.stock;

import com.shop.common.ApiException;
import com.shop.common.PageDto;
import com.shop.product.Product;
import com.shop.product.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/** Ton kho, the kho, dieu chinh / xuat huy, muc ton toi thieu. Chi ADMIN. */
@RestController
@RequestMapping("/api/stock")
public class StockController {
    private final StockBalanceRepository balances;
    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final ProductUnitRepository units;
    private final StockService stock;

    public StockController(StockBalanceRepository balances, StockMovementRepository movements, ProductRepository products,
                           ProductUnitRepository units, StockService stock) {
        this.balances = balances; this.movements = movements; this.products = products; this.units = units; this.stock = stock;
    }

    public record StockRow(Long productId, String name, String categoryName, String unit, BigDecimal onHand, BigDecimal avgCost,
                           BigDecimal value, BigDecimal minStock, BigDecimal price) {}
    public record AdjustRequest(@NotNull BigDecimal qty, String reason) {}
    public record MinStockRequest(@NotNull @DecimalMin("0") BigDecimal minStock) {}

    private static StockRow row(StockBalance b, Product p, String cat) {
        BigDecimal value = b.getOnHand().signum() > 0 ? b.getOnHand().multiply(b.getAvgCost()).setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return new StockRow(p.getId(), p.getName(), cat, p.getUnit(), b.getOnHand(), b.getAvgCost(), value, p.getMinStock(), p.getPrice());
    }

    /** Sap xep: ten, ton, gia von, gia tri ton (bieu thuc nen dung JpaSort.unsafe). */
    private static final Map<String, String> SORTABLE = Map.of("name", "p.name", "onHand", "b.onHand", "avgCost", "b.avgCost",
            "value", "b.onHand * b.avgCost", "minStock", "p.minStock");

    @GetMapping
    @Transactional(readOnly = true)
    public PageDto<StockRow> list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") long categoryId,
                                  @RequestParam(defaultValue = "all") String filter,
                                  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(defaultValue = "") String sort) {
        if (!List.of("all", "low", "negative", "out").contains(filter)) filter = "all";
        Sort s = JpaSort.unsafe(Sort.Direction.ASC, "p.name");
        if (!sort.isBlank()) {
            String[] parts = sort.split(",");
            String expr = SORTABLE.get(parts[0]);
            if (expr != null) s = JpaSort.unsafe(parts.length > 1 && parts[1].equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, expr)
                    .and(JpaSort.unsafe(Sort.Direction.ASC, "p.name"));
        }
        var p = balances.stockPage(PageDto.like(q), categoryId, filter,
                PageRequest.of(Math.max(0, page), Math.min(PageDto.MAX_SIZE, Math.max(1, size)), s));
        Object[] sum = balances.stockSummary().get(0);
        return PageDto.of(p, r -> row((StockBalance) r[0], (Product) r[1], (String) r[2]), Map.of(
                "count", sum[0], "totalValue", ((BigDecimal) sum[1]).setScale(0, RoundingMode.HALF_UP),
                "lowCount", sum[2] == null ? 0 : ((Number) sum[2]).longValue(),
                "negativeCount", sum[3] == null ? 0 : ((Number) sum[3]).longValue()));
    }

    /** Ton + gia von + don vi quy doi cua 1 san pham (dau the kho). */
    @GetMapping("/{productId}")
    @Transactional(readOnly = true)
    public Map<String, Object> get(@PathVariable Long productId) {
        Product p = products.findById(productId).orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
        StockBalance b = balances.findById(productId).orElseGet(() -> { StockBalance x = new StockBalance(); x.setProductId(productId); return x; });
        return Map.of("stock", row(b, p, p.getCategory() == null ? null : p.getCategory().getName()),
                "units", units.findByProductIdOrderByUnit(productId));
    }

    /** The kho: cac dong so kho cua 1 san pham, moi nhat truoc. */
    @GetMapping("/{productId}/movements")
    public PageDto<StockMovement> movements(@PathVariable Long productId, @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return PageDto.of(movements.findByProductIdOrderByIdDesc(productId, PageDto.pageable(page, size, null, Map.of(), Sort.unsorted())), x -> x, null);
    }

    /** Dieu chinh ton: qty am = xuat huy / hao hut; duong = nhap them khong qua phieu (theo gia von hien tai). */
    @PostMapping("/{productId}/adjust")
    @Transactional
    public StockRow adjust(@PathVariable Long productId, @Valid @RequestBody AdjustRequest r, Authentication a) {
        Product p = products.findById(productId).orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
        if (r.qty().signum() == 0) throw ApiException.badRequest("Số lượng điều chỉnh phải khác 0");
        if (r.reason() == null || r.reason().isBlank()) throw ApiException.badRequest("Nhập lý do điều chỉnh (hao hụt, hư hỏng, cắt lẻ...)");
        stock.apply(new StockService.Move(productId, r.qty(), null, StockMovement.Type.ADJUST, StockService.REF_ADJUST,
                null, null, null, r.reason().trim(), ReceiptController.who(a)));
        StockBalance b = balances.findById(productId).orElseThrow();
        return row(b, p, p.getCategory() == null ? null : p.getCategory().getName());
    }

    @PutMapping("/{productId}/min-stock")
    @Transactional
    public void minStock(@PathVariable Long productId, @Valid @RequestBody MinStockRequest r) {
        products.findById(productId).orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm")).setMinStock(r.minStock());
    }
}
