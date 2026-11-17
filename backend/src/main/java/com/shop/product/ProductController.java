package com.shop.product;

import com.shop.common.PageDto;
import com.shop.stock.ProductUnit;
import com.shop.stock.ProductUnitRepository;
import com.shop.stock.StockBalance;
import com.shop.stock.StockService;
import org.springframework.data.domain.Sort;

import com.shop.common.ApiException;
import com.shop.common.TextSearch;
import com.shop.order.Order;
import com.shop.order.OrderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final StockService stock;
    private final ProductUnitRepository units;
    private final OrderRepository orders;

    public ProductController(ProductRepository products, CategoryRepository categories, StockService stock,
                             ProductUnitRepository units, OrderRepository orders) {
        this.products = products; this.categories = categories; this.stock = stock; this.units = units; this.orders = orders;
    }

    public record ProductRequest(@NotBlank String name, Long categoryId, @NotBlank(message = "Bạn chưa nhập đơn vị cho sản phẩm") String unit,
                                 @NotNull @DecimalMin("0") BigDecimal price, BigDecimal minStock) {}
    /** onHand: ton hien tai (don vi goc) - man Ban hang dung de canh bao ban qua ton. */
    public record ProductDto(Long id, String name, Long categoryId, String categoryName, String unit, BigDecimal price,
                             BigDecimal minStock, BigDecimal onHand) {}
    public record UnitDto(String unit, BigDecimal ratio) {}
    public record CategoryRequest(@NotBlank String name) {}

    private ProductDto dto(Product p) { return dto(p, null); }

    private ProductDto dto(Product p, StockBalance b) {
        Category c = p.getCategory();
        return new ProductDto(p.getId(), p.getName(), c == null ? null : c.getId(),
                c == null ? null : c.getName(), p.getUnit(), p.getPrice(), p.getMinStock(), b == null ? null : b.getOnHand());
    }

    private static final Map<String, String> SORTABLE = Map.of("name", "name", "price", "price", "unit", "unit");

    /**
     * Danh sach san pham co phan trang. active=false: san pham da ngung ban (de ban lai).
     * categoryId: 0 moi loai, -1 chua phan loai.
     */
    @GetMapping("/products")
    @Transactional(readOnly = true)
    public PageDto<ProductDto> list(@RequestParam(defaultValue = "") String q,
                                    @RequestParam(defaultValue = "0") long categoryId,
                                    @RequestParam(defaultValue = "true") boolean active,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(defaultValue = "") String sort) {
        if (q.isBlank()) {
            var p = products.page("", categoryId, active,
                    PageDto.pageable(page, size, sort, SORTABLE, Sort.by("name").and(Sort.by("id"))));
            var bal = stock.balancesOf(p.getContent().stream().map(Product::getId).toList()); // 1 truy van cho ca trang
            return PageDto.of(p, x -> dto(x, bal.get(x.getId())), null);
        }
        // Co chu tim: khong dau, theo tu, khong can dung thu tu; tim ca theo ten loai hang ("nhom" -> moi mat hang loai Nhôm).
        // Xep hang: khop nguyen tu > dau tu > giua tu, roi mat hang ban nhieu len truoc (man Ban hang: Enter chon ket qua dau).
        Map<Long, Long> sold = new HashMap<>();
        for (Object[] r : orders.countLinesByProduct(Order.Status.PAID)) sold.put((Long) r[0], (Long) r[1]);
        List<Product> pool = products.page("", categoryId, active, org.springframework.data.domain.Pageable.unpaged()).getContent();
        List<Product> hit = TextSearch.rank(pool, q,
                p -> new String[]{p.getName(), p.getCategory() == null ? null : p.getCategory().getName()},
                p -> sold.getOrDefault(p.getId(), 0L));
        if (!sort.isBlank()) hit = sortedProducts(hit, sort);
        int sz = Math.min(PageDto.MAX_SIZE, Math.max(1, size)), from = Math.min(hit.size(), Math.max(0, page) * sz);
        var bal = stock.balancesOf(hit.subList(from, Math.min(hit.size(), from + sz)).stream().map(Product::getId).toList());
        return PageDto.ofList(hit, page, size, x -> dto(x, bal.get(x.getId())), null);
    }

    private static List<Product> sortedProducts(List<Product> rows, String sort) {
        String[] p = sort.split(",");
        java.util.Comparator<Product> cmp = switch (p[0]) {
            case "name" -> java.util.Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER);
            case "price" -> java.util.Comparator.comparing(Product::getPrice);
            default -> null;
        };
        if (cmp == null) return rows;
        if (!(p.length > 1 && p[1].equalsIgnoreCase("asc"))) cmp = cmp.reversed();
        return rows.stream().sorted(cmp).toList();
    }

    /** Don vi quy doi khi nhap hang (1 don vi = ratio don vi goc). */
    @GetMapping("/products/{id}/units")
    public List<UnitDto> units(@PathVariable Long id) {
        return units.findByProductIdOrderByUnit(id).stream().map(u -> new UnitDto(u.getUnit(), u.getRatio())).toList();
    }

    /** Thay toan bo don vi quy doi. Sua ty le khong anh huong phieu nhap cu (so luong da quy doi duoc luu tren phieu). */
    @PutMapping("/products/{id}/units")
    @Transactional
    public List<UnitDto> setUnits(@PathVariable Long id, @RequestBody List<UnitDto> list) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (UnitDto u : list) {
            if (u.unit() == null || u.unit().isBlank()) throw ApiException.badRequest("Có đơn vị quy đổi chưa nhập tên");
            if (u.unit().trim().equalsIgnoreCase(p.getUnit())) throw ApiException.badRequest("Đơn vị \"" + u.unit() + "\" trùng đơn vị gốc");
            if (u.ratio() == null || u.ratio().signum() <= 0) throw ApiException.badRequest("Tỷ lệ quy đổi của \"" + u.unit() + "\" phải lớn hơn 0");
            if (!seen.add(u.unit().trim().toLowerCase())) throw ApiException.badRequest("Đơn vị \"" + u.unit() + "\" bị trùng");
        }
        units.deleteAllOfProduct(id);
        units.flush();
        for (UnitDto u : list) {
            ProductUnit x = new ProductUnit();
            x.setProductId(id);
            x.setUnit(u.unit().trim());
            x.setRatio(u.ratio());
            units.save(x);
        }
        return units(id);
    }

    /** Ban lai san pham da ngung ban. */
    @PostMapping("/products/{id}/restore")
    @Transactional
    public ProductDto restore(@PathVariable Long id) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
        p.setActive(true);
        return dto(p);
    }

    @PostMapping("/products")
    @Transactional
    public ProductDto create(@Valid @RequestBody ProductRequest r) {
        Product p = new Product();
        apply(p, r);
        p = products.save(p);
        stock.ensureBalance(p.getId()); // san pham moi: ton 0
        return dto(p);
    }

    @PutMapping("/products/{id}")
    @Transactional
    public ProductDto update(@PathVariable Long id, @Valid @RequestBody ProductRequest r) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
        apply(p, r);
        return dto(p);
    }

    /** Xoa mem: giu lai de hoa don cu van tham chieu duoc. */
    @DeleteMapping("/products/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        products.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm")).setActive(false);
    }

    private void apply(Product p, ProductRequest r) {
        p.setName(r.name().trim());
        p.setUnit(r.unit());
        p.setPrice(r.price());
        if (r.minStock() != null) {
            if (r.minStock().signum() < 0) throw ApiException.badRequest("Tồn tối thiểu không được âm");
            p.setMinStock(r.minStock());
        }
        p.setCategory(r.categoryId() == null ? null : categories.findById(r.categoryId())
                .orElseThrow(() -> ApiException.badRequest("Loại hàng không tồn tại")));
    }

    public record CategoryDto(Long id, String name, long productCount) {}

    /** Loai hang kem so san pham dang ban, sap theo ten. */
    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return products.categoryCounts().stream()
                .map(r -> new CategoryDto((Long) r[0], (String) r[1], ((Number) r[2]).longValue())).toList();
    }

    @PutMapping("/categories/{id}")
    @Transactional
    public Category updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest r) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy loại hàng"));
        if (categories.existsByNameIgnoreCaseAndIdNot(r.name().trim(), id)) throw ApiException.conflict("Tên loại hàng đã tồn tại");
        c.setName(r.name().trim());
        return c;
    }

    @DeleteMapping("/categories/{id}")
    @Transactional
    public void deleteCategory(@PathVariable Long id) {
        if (products.existsByCategoryIdAndActiveTrue(id))
            throw ApiException.conflict("Loại hàng đang có sản phẩm. Chuyển sản phẩm sang loại khác trước khi xóa.");
        products.clearCategory(id); // san pham da xoa mem van giu tham chieu -> bo tham chieu de xoa duoc
        categories.deleteById(id);
    }

    @PostMapping("/categories")
    public Category createCategory(@Valid @RequestBody CategoryRequest r) {
        // trung ten: bao ro (truoc day rang buoc UNIQUE cua DB tra loi 500 "May chu gap loi")
        if (categories.existsByNameIgnoreCase(r.name().trim())) throw ApiException.conflict("Tên loại hàng đã tồn tại");
        Category c = new Category();
        c.setName(r.name().trim());
        return categories.save(c);
    }
}
