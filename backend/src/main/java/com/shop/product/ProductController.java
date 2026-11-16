package com.shop.product;

import com.shop.common.PageDto;
import org.springframework.data.domain.Sort;
import java.util.Map;

import com.shop.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductRepository products;
    private final CategoryRepository categories;

    public ProductController(ProductRepository products, CategoryRepository categories) {
        this.products = products; this.categories = categories;
    }

    public record ProductRequest(@NotBlank String name, Long categoryId, @NotBlank(message = "Bạn chưa nhập đơn vị cho sản phẩm") String unit,
                                 @NotNull @DecimalMin("0") BigDecimal price) {}
    public record ProductDto(Long id, String name, Long categoryId, String categoryName, String unit, BigDecimal price) {}
    public record CategoryRequest(@NotBlank String name) {}

    private ProductDto dto(Product p) {
        Category c = p.getCategory();
        return new ProductDto(p.getId(), p.getName(), c == null ? null : c.getId(),
                c == null ? null : c.getName(), p.getUnit(), p.getPrice());
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
        var p = products.page(PageDto.like(q), categoryId, active,
                PageDto.pageable(page, size, sort, SORTABLE, Sort.by("name").and(Sort.by("id"))));
        return PageDto.of(p, this::dto, null);
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
        return dto(products.save(p));
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
        Category c = new Category();
        c.setName(r.name().trim());
        return categories.save(c);
    }
}
